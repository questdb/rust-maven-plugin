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

package io.questdb.rust_maven_example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public interface JniJarLoader {
    public static void loadLib(String jarPathPrefix, Lib lib) {
        String pathInJar = jarPathPrefix + lib.getFullName();
        InputStream is = JniJarLoader.class.getResourceAsStream(pathInJar);
        if (is == null) {
            throw new LoadException("Internal error: cannot find " + pathInJar + ", broken package?");
        }

        try {
            File tempLib = null;
            try {
                int dot = pathInJar.indexOf('.');
                tempLib = File.createTempFile(pathInJar.substring(0, dot), pathInJar.substring(dot));
                // copy to tempLib
                try (FileOutputStream out = new FileOutputStream(tempLib)) {
                    byte[] buf = new byte[4096];
                    while (true) {
                        int read = is.read(buf);
                        if (read == -1) {
                            break;
                        }
                        out.write(buf, 0, read);
                    }
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

    public static class LoadException extends RuntimeException {
        public LoadException(String message) {
            super(message);
        }

        public LoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static enum OsLibConventions {
        INSTANCE();

        private final String libPrefix;
        private final String libSuffix;

        private OsLibConventions() {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                this.libPrefix = "";
                this.libSuffix = ".dll";
            } else if (osName.contains("mac")) {
                this.libPrefix = "lib";
                this.libSuffix = ".dylib";
            } else {
                this.libPrefix = "lib";
                this.libSuffix = ".so";
            }
        }

        public String getLibPrefix() {
            return libPrefix;
        }

        public String getLibSuffix() {
            return libSuffix;
        }
    }

    public static class Lib {
        private String name;
        private String prefix;
        private String suffix;
        public Lib (String name) {
            this.name = name;
            this.prefix = OsLibConventions.INSTANCE.getLibPrefix();
            this.suffix = OsLibConventions.INSTANCE.getLibSuffix();
        }

        public Lib (String name, String prefix, String suffix) {
            this.name = name;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public String getName() {
            return name;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public String getFullName() {
            return prefix + name + suffix;
        }
    }
}
