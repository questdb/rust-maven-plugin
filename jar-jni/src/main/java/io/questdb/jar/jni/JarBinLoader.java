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

package io.questdb.jar.jni;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads and extracts native binaries from JAR files.
 */
public interface JarBinLoader {

    /**
     * Loads and extracts a native binary from a JAR file.
     *
     * @param cls             The class to use for loading the binary.
     * @param jarPathPrefix   The path prefix to the binary in the JAR file.
     * @param name            The name of the binary.
     * @param platformDir     The platform-specific subdirectory (inside jarPathPrefix) to load the binary from,
     *                        or null to search for the binary directly within jarPathPrefix.
     * @param autoDelete      Whether to automatically delete the extracted binary when the JVM exits.
     * @return The absolute path to the extracted binary file.
     */
    static <T> String loadBin(Class<T> cls, String jarPathPrefix, String name, String platformDir, boolean autoDelete) {
        final String sep = jarPathPrefix.endsWith("/") ? "" : "/";
        String pathInJar = jarPathPrefix + sep;
        if (platformDir != null) {
            pathInJar += platformDir + "/";
        }

        // Determine binary suffix based on OS
        String suffix = "";
        if (Platform.isWindows()) {
            suffix = ".exe";
        }

        pathInJar += name + suffix;

        final InputStream is = cls.getResourceAsStream(pathInJar);
        if (is == null) {
            throw new LoadException("Internal error: cannot find " + pathInJar + ", broken package?");
        }

        try {
            File tempBin = null;
            try {
                tempBin = File.createTempFile(name, suffix);
                // copy to tempBin
                try (FileOutputStream out = new FileOutputStream(tempBin)) {
                    StreamTransfer.copyToStream(is, out);
                } finally {
                    if (autoDelete) {
                        tempBin.deleteOnExit();
                    }
                }
                return tempBin.getAbsolutePath();
            } catch (IOException e) {
                throw new LoadException("Internal error: cannot unpack " + tempBin, e);
            }
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Loads and extracts a native binary from a JAR file, automatically determining the
     * platform-specific subdirectory based on the current operating system and architecture.
     *
     * <p>This method simplifies the process of loading binaries that are organized within a JAR
     * file according to a platform-specific directory structure. It leverages the
     * {@link Platform#RESOURCE_PREFIX} to infer the correct subdirectory (e.g., "windows-amd64",
     * "linux-amd64", "mac_os_x-arm64").
     *
     * <p><b>Usage Example:</b></p>
     *
     * <p>Suppose you have a JAR file with the following structure:</p>
     *
     * <pre>
     * myapp.jar
     * └── native
     *     ├── linux-amd64
     *     │   └── mybinary
     *     ├── windows-amd64
     *     │   └── mybinary.exe
     *     └── mac_os_x-arm64
     *         └── mybinary
     * </pre>
     *
     * <p>To load the "mybinary" for the current platform and have it automatically deleted on JVM exit, you would use:</p>
     *
     * <pre>{@code
     * String binaryPath = JarBinLoader.loadBin(MyClass.class, "/native", "mybinary", true);
     * // On Windows, binaryPath might be: "C:\Users\...\AppData\Local\Temp\mybinary12345.exe"
     * // On Linux, binaryPath might be: "/tmp/mybinary67890"
     * // On macOS (ARM), binaryPath might be: "/tmp/mybinary54321"
     *
     * // Now you can use 'binaryPath' to execute the extracted binary.
     * // ...
     * }</pre>
     *
     * <p>If you want to keep the extracted binary after the JVM exits (e.g., for debugging or reuse), set `autoDelete` to `false`:</p>
     *
     * <pre>{@code
     * String binaryPath = JarBinLoader.loadBin(MyClass.class, "/native", "mybinary", false);
     * // The extracted binary will remain in the temporary directory even after the program finishes.
     * }</pre>
     *
     * @param cls             The class whose class loader will be used to locate the binary within the JAR.
     *                        Typically, this is a class from the same JAR as the binary.
     * @param jarPathPrefix   The path prefix within the JAR where the platform-specific directories are located.
     *                        For example, "/native" if your binaries are in "/native/linux-amd64", etc.
     * @param name            The name of the binary file (without any platform-specific prefix or suffix).
     *                        The method automatically handles the differences in naming conventions between
     *                        Windows (which uses ".exe" suffix) and other operating systems.
     * @param autoDelete      {@code true} if the extracted binary file should be automatically deleted when the
     *                        JVM exits; {@code false} otherwise. Setting to {@code false} can be useful for
     *                        debugging or if you intend to reuse the binary.
     * @return The absolute path to the extracted binary file on the local file system.
     * @throws LoadException If the binary cannot be found in the JAR, or if there is an error during
     *                       the extraction process.
     */
    static <T> String loadBin(Class<T> cls, String jarPathPrefix, String name, boolean autoDelete) {
        return loadBin(cls, jarPathPrefix, name, Platform.RESOURCE_PREFIX, autoDelete);
    }
}