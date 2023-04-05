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
 * Loads native libraries from JAR files.
 */
public interface JarJniLoader {
    /**
     * Loads a native library from a JAR file.
     *
     * @param cls           The class to use for loading the library.
     * @param jarPathPrefix The path prefix to the library in the JAR file.
     * @param name          The name of the library, sans "lib" prefix and ".so|.dll|.dylib" suffix.
     * @param platformDir   The platform-specific subdirectory (inside jarPathPrefix) to load the library from,
     *                      or null to search for the dynamic library directly within jarPathPrefix.
     */
    static <T> void loadLib(Class<T> cls, String jarPathPrefix, String name, String platformDir) {
        final String sep = jarPathPrefix.endsWith("/") ? "" : "/";
        String pathInJar = jarPathPrefix + sep;
        if (platformDir != null) {
            pathInJar += platformDir + "/";
        }
        pathInJar += Platform.LIB_PREFIX + name + Platform.LIB_SUFFIX;
        final InputStream is = cls.getResourceAsStream(pathInJar);
        if (is == null) {
            throw new LoadException("Internal error: cannot find " + pathInJar + ", broken package?");
        }

        try {
            File tempLib = null;
            try {
                final int dot = pathInJar.indexOf('.');
                tempLib = File.createTempFile(pathInJar.substring(0, dot), pathInJar.substring(dot));
                // copy to tempLib
                try (FileOutputStream out = new FileOutputStream(tempLib)) {
                    StreamTransfer.copyToStream(is, out);
                } finally {
                    tempLib.deleteOnExit();
                }
                System.load(tempLib.getAbsolutePath());
            } catch (IOException e) {
                throw new LoadException("Internal error: cannot unpack " + tempLib, e);
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
     * Loads a native library from a JAR file.
     * <p>
     * The library is loaded from the platform-specific subdirectory of the jarPathPrefix.
     * <p>
     * The platform-specific subdirectory derived from the current platform and
     * the architecture of the JVM as determined by {@link OsInfo#PLATFORM}.
     * <p>
     * For example if executing, <code>JarJniLoader.loadLib(MyClass.class, "/native", "mylib");</code>
     * on a 64-bit x86 Linux system, the library will be loaded from "/native/linux-amd64/libmylib.so"
     * from the same JAR file that contains MyClass.class.
     * If executing on an Apple Silicon Macbook, the library will be loaded from
     * "/native/mac_os_x-arm64/libmylib.dylib".
     * From Windows 11, the library will be loaded from "/native/windows-amd64/mylib.dll" (note, no "lib" prefix).
     *
     * @param cls           The class to use for loading the library.
     * @param jarPathPrefix The path prefix to the library in the JAR file.
     * @param name          The name of the library, sans "lib" prefix and ".so|.dll|.dylib" suffix.
     */
    static <T> void loadLib(Class<T> cls, String jarPathPrefix, String name) {
        loadLib(cls, jarPathPrefix, name, Platform.RESOURCE_PREFIX);
    }
}
