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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public abstract class CargoMojoBase extends AbstractMojo {

    /**
     * Path to the `cargo` command. If unset or set to "cargo", uses $PATH.
     */
    @Parameter(property = "cargo.path", defaultValue = "cargo")
    private String cargoPath;

    @Parameter(property = "project", readonly = true)
    protected MavenProject project;

    protected String getCargoPath() {
        String path = cargoPath;

        final boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        // Expand "~" to user's home directory.
        // This works around a limitation of ProcessBuilder.
        if (!isWindows && cargoPath.startsWith("~/")) {
            path = System.getProperty("user.home") + cargoPath.substring(1);
        }

        return path;
    }

    private void runCommand(List<String> args)
            throws IOException, InterruptedException {
        final ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);

        // Set the current working directory for the cargo command.
        processBuilder.directory(project.getBasedir());
        final Process process = processBuilder.start();
        final StringBuilder output = new StringBuilder();
        Executors.newSingleThreadExecutor().submit(() -> {
            new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines()
                    .forEach(output::append);
        });

        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            String indentedOutput = output.toString().replaceAll("(?m)^", "      ");
            StringBuilder descr = new StringBuilder();
            descr.append('\n');
            descr.append("  - Command: ");
            for (int i = 0; i < args.size(); i++) {
                final String escapedArg = Shlex.quote(args.get(i));
                descr.append(escapedArg);
                final char terminator = i < args.size() - 1 ? ' ' : '\n';
                descr.append(terminator);
            }
            descr.append("  - Exit code: ").append(exitCode).append('\n');
            descr.append("  - Project directory: ")
                    .append(processBuilder.directory().getAbsolutePath())
                    .append('\n');
            descr.append("  - Command output:\n").append(indentedOutput).append('\n');
            throw new IOException(descr.toString());
        }
    }

    protected void cargo(List<String> args) throws MojoExecutionException {
        String cargoPath = getCargoPath();
        final List<String> cmd = new ArrayList<>();
        cmd.add(cargoPath);
        cmd.addAll(args);
        try {
            runCommand(cmd);
        } catch (IOException | InterruptedException e) {
            CargoInstalledChecker.INSTANCE.check(getLog(), cargoPath);
            throw new MojoExecutionException("Failed to invoke cargo", e);
        }
    }
}
