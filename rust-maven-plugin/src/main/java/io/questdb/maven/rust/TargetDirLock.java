/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.maven.rust;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Exclusive lock guarding a single cargo target directory across a whole
 * build-and-copy (or test) critical section.
 * <p>
 * When several checkouts share one target directory (see the plugin's
 * `targetRootDir` parameter), cargo already serializes the builds themselves via
 * its own lock, but it releases that lock as soon as it exits. The final artifact
 * cargo leaves in the `<profile>` directory is not fingerprinted, so a second
 * checkout could overwrite it in the window between `cargo` exiting and this plugin
 * copying the artifact to its own destination - silently packaging another
 * checkout's binary. Holding this lock from before `cargo` starts until after the
 * copy completes closes that window.
 * <p>
 * The lock is both cross-process (an OS {@link FileLock}) and in-JVM (a
 * {@link ReentrantLock}). The in-JVM lock is required because a `FileLock` is owned
 * by the whole JVM: two threads in the same reactor (e.g. a parallel `mvn -T` build)
 * would otherwise race to lock the same file and hit an
 * `OverlappingFileLockException` instead of being serialized.
 * <p>
 * Scope: this is an advisory lock file that only invocations of this plugin acquire.
 * It does not coordinate with a standalone `cargo` build (or an IDE / rust-analyzer)
 * run against the same target directory; such a build can still overwrite the final
 * artifact in the copy window. Tools use their own target directory by default, so a
 * shared directory should only ever be built into by this plugin.
 */
final class TargetDirLock implements AutoCloseable {
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS =
            new ConcurrentHashMap<>();

    private final ReentrantLock jvmLock;
    private final FileChannel channel;
    private final FileLock fileLock;

    private TargetDirLock(ReentrantLock jvmLock, FileChannel channel, FileLock fileLock) {
        this.jvmLock = jvmLock;
        this.channel = channel;
        this.fileLock = fileLock;
    }

    /**
     * Acquires the lock for the given cargo target directory, blocking until it is
     * available. The lock file lives next to the target directory (not inside it, so
     * it never interferes with cargo's management of the directory's contents) and is
     * named after it, so checkouts sharing a target directory contend on the same file.
     */
    static TargetDirLock acquire(Path targetDir) throws MojoExecutionException {
        final Path parent = targetDir.toAbsolutePath().getParent();
        final Path lockFile = parent.resolve(targetDir.getFileName().toString() + ".lock");
        final ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockFile, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Files.createDirectories(parent);
            final FileChannel channel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            try {
                final FileLock fileLock = channel.lock();
                return new TargetDirLock(jvmLock, channel, fileLock);
            } catch (IOException e) {
                channel.close();
                throw e;
            }
        } catch (IOException e) {
            jvmLock.unlock();
            throw new MojoExecutionException(
                    "Failed to acquire build lock for target directory " + targetDir, e);
        }
    }

    @Override
    public void close() throws MojoExecutionException {
        try {
            fileLock.release();
            channel.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to release build lock", e);
        } finally {
            jvmLock.unlock();
        }
    }
}
