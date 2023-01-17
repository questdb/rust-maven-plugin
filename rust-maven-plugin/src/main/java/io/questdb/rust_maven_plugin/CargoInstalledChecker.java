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

package io.questdb.rust_maven_plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.util.HashMap;

public final class CargoInstalledChecker {

    public static final CargoInstalledChecker INSTANCE =
            new CargoInstalledChecker();

    private final HashMap<String, InstalledState> cache = new HashMap<>();

    private CargoInstalledChecker() {
    }

    public synchronized void check(Log log, String cargoPath)
            throws MojoExecutionException {
        InstalledState cached = cache.getOrDefault(
                cargoPath, InstalledState.UNKNOWN);

        if (cached == InstalledState.UNKNOWN) {
            try {
                final ProcessBuilder pb = new ProcessBuilder(
                        cargoPath, "--version");
                final Process p = pb.start();
                final int exitCode = p.waitFor();
                cached = exitCode == 0
                        ? InstalledState.INSTALLED
                        : InstalledState.BROKEN;
                cache.put(cargoPath, cached);
            } catch (Exception e) {
                cached = InstalledState.NOT_INSTALLED;
                cache.put(cargoPath, cached);
            }
        }

        if (cached == InstalledState.INSTALLED) {
            return;
        }

        final StringBuilder error = new StringBuilder();

        if (cached == InstalledState.BROKEN) {
            if (cargoPath.equals("cargo")) {
                error.append("Rust's `cargo` ");
            } else {
                error
                        .append("Rust's `cargo` at ")
                        .append(Shlex.quote(cargoPath));
            }
            error.append(
                    " is a broken install: Running `cargo --version` " +
                            "returned non-zero exit code");
        } else {  // cached == InstalledState.NOT_INSTALLED
            if (cargoPath.equals("cargo")) {
                error
                        .append("Rust's `cargo` not found in PATH=")
                        .append(Shlex.quote(System.getenv("PATH")));
            } else {
                error
                        .append("Rust's `cargo` not found at ")
                        .append(Shlex.quote(cargoPath));
            }
        }
        error.append(". See https://www.rust-lang.org/tools/install");
        throw new MojoExecutionException(error.toString());
    }

    private enum InstalledState {
        UNKNOWN,
        NOT_INSTALLED,
        INSTALLED,
        BROKEN
    }
}
