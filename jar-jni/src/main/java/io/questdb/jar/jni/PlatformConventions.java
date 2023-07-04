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

public class PlatformConventions {
    public static final String LIB_PREFIX;
    public static final String LIB_SUFFIX;
    public static final String EXE_SUFFIX = isWindows() ? ".exe" : "";

    private static boolean isWindows() {
        switch (Platform.getOSType()) {
            case Platform.WINDOWS:
            case Platform.WINDOWSCE:
                return true;
            default:
                return false;
        }
    }

    static {
        switch (Platform.getOSType()) {
            case Platform.LINUX:
            case Platform.SOLARIS:
            case Platform.FREEBSD:
            case Platform.OPENBSD:
            case Platform.ANDROID:
            case Platform.GNU:
            case Platform.KFREEBSD:
            case Platform.NETBSD:
                LIB_PREFIX = "lib";
                LIB_SUFFIX = ".so";
                break;

            case Platform.MAC:
                LIB_PREFIX = "lib";
                LIB_SUFFIX = ".dylib";
                break;

            case Platform.WINDOWS:
            case Platform.WINDOWSCE:
                LIB_PREFIX = "";
                LIB_SUFFIX = ".dll";
                break;

            case Platform.AIX:
                LIB_PREFIX = "lib";
                LIB_SUFFIX = ".a";
                break;

            default:
                throw new IllegalStateException("Unsupported platform: " + Platform.getOSType());

        }
    }
}
