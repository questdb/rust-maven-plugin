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

package io.questdb.rust.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * An example of a Maven plugin.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class CargoBuildMojo extends AbstractMojo {

    @Parameter(property = "project", readonly = true)
    protected MavenProject project;

    @Parameter(property = "environmentVariables")
    HashMap<String, String> environmentVariables;

    /**
     * Path to the `cargo` command. If unset or set to "cargo", uses $PATH.
     */
    @Parameter(property = "cargo.path", defaultValue = "cargo")
    private String cargoPath;

    /**
     * Path to the Rust crate (or workspace) to build.
     */
    @Parameter(property = "path", required = true)
    private String path;

    /**
     * Build artifacts in release mode, with optimizations.
     * Defaults to "false" and creates a debug build.
     * Equivalent to Cargo's `--release` option.
     */
    @Parameter(property = "release", defaultValue = "false")
    private boolean release;

    /**
     * List of features to activate.
     * If not specified, default features are activated.
     * Equivalent to Cargo's `--features` option.
     */
    @Parameter(property = "features")
    private String[] features;

    /**
     * Activate all available features.
     * Defaults to "false".
     * Equivalent to Cargo's `--all-features` option.
     */
    @Parameter(property = "all-features", defaultValue = "false")
    private boolean allFeatures;

    /**
     * Do not activate the `default` feature.
     * Defaults to "false".
     * Equivalent to Cargo's `--no-default-features` option.
     */
    @Parameter(property = "no-default-features", defaultValue = "false")
    private boolean noDefaultFeatures;

    /**
     * Build all tests.
     * Defaults to "false".
     * Equivalent to Cargo's `--tests` option.
     */
    @Parameter(property = "tests", defaultValue = "false")
    private boolean tests;

    /**
     * Additional args to pass to cargo.
     */
    @Parameter(property = "extra-args")
    private String[] extraArgs;

    /**
     * Location to copy the built Rust binaries to.
     * If unset, the binaries are not copied and remain in the target directory.
     *
     * See also `copyWithPlatformDir`.
     */
    @Parameter(property = "copyTo")
    private String copyTo;

    /**
     * Further nest copy into a subdirectory named through the following expression:
     * (System.getProperty("os.name") + "_" + System.getProperty("os.arch")).toLowerCase();
     *
     * See also `copyTo`.
     */
    @Parameter(property = "copyWithPlatformDir")
    private boolean copyWithPlatformDir;

    private String getCargoPath() {
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

    private File getPath() {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        } else {
            return new File(project.getBasedir(), path);
        }
    }

    private String getName() {
        final String[] components = path.split("/");
        return components[components.length - 1];
    }

    private File getTargetDir() {
        return new File(new File(project.getBuild().getDirectory()), getName());
    }

    private void runCommand(List<String> args)
            throws IOException, InterruptedException, MojoExecutionException {
        final ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putAll(environmentVariables);

        // Set the current working directory for the cargo command.
        processBuilder.directory(getPath());
        final Process process = processBuilder.start();
        Log log = getLog();
        Executors.newSingleThreadExecutor().submit(() -> {
            new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines()
                    .forEach(log::info);
        });

        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new MojoExecutionException("Cargo command failed with exit code " + exitCode);
        }
    }

    private void cargo(List<String> args) throws MojoExecutionException {
        String cargoPath = getCargoPath();
        final List<String> cmd = new ArrayList<>();
        cmd.add(cargoPath);
        cmd.addAll(args);
        getLog().info("Working directory: " + getPath());
        if (!environmentVariables.isEmpty()) {
            getLog().info("Environment variables:");
            for (String key : environmentVariables.keySet()) {
                getLog().info("  " + key + "=" + Shlex.quote(environmentVariables.get(key)));
            }
        }
        getLog().info("Running: " + Shlex.quote(cmd));
        try {
            runCommand(cmd);
        } catch (IOException | InterruptedException e) {
            CargoInstalledChecker.INSTANCE.check(getLog(), cargoPath);
            throw new MojoExecutionException("Failed to invoke cargo", e);
        }
    }

    private File getCopyToDir() throws MojoExecutionException {
        File copyToDir = new File(copyTo);
        if (!copyToDir.isAbsolute()) {
            copyToDir = new File(project.getBasedir(), copyTo);
        }
        if (!copyToDir.exists()) {
            if (!copyToDir.mkdirs()) {
                throw new MojoExecutionException("Failed to create directory " + copyToDir);
            }
        }
        if (!copyToDir.isDirectory()) {
            throw new MojoExecutionException(copyToDir + " is not a directory");
        }
        if (copyWithPlatformDir) {
            final String platform = (System.getProperty("os.name") + "_" + System.getProperty("os.arch")).toLowerCase();
            copyToDir = new File(copyToDir, platform);
        }
        return copyToDir;
    }

    private File findArtifactPath() throws MojoExecutionException {
        final File targetDir = getTargetDir();
        final String buildType = release ? "release" : "debug";
        final String osName = System.getProperty("os.name").toLowerCase();
        final String libPrefix = osName.startsWith("windows") ? "" : "lib";
        final String libSuffix = osName.startsWith("windows")
                ? ".dll" : osName.contains("mac")
                ? ".dylib" : ".so";
        final String artifactName = libPrefix + getName().replace('-', '_') + libSuffix;
        final File artifactPath = new File(targetDir, buildType + "/" + artifactName);
        if (!artifactPath.exists()) {
            throw new MojoExecutionException("Artifact not found: " + Shlex.quote(artifactPath.toString()));
        }
        return artifactPath;
    }

    private void copyArtifacts() throws MojoExecutionException {
        if (copyTo == null) {
            getLog().info("Not copying artifacts <copyTo> is not set");
            return;
        }

        getLog().info("Copying artifacts to " + Shlex.quote(copyTo));

        final File getArtifactPath = findArtifactPath();
        final File copyToDir = getCopyToDir();
        final File copyToPath = new File(copyToDir, getArtifactPath.getName());
        try {
            Files.copy(getArtifactPath.toPath(), copyToPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy " + getArtifactPath + " to " + copyToPath, e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        List<String> args = new ArrayList<>();
        args.add("build");

        args.add("--target-dir");
        args.add(getTargetDir().getAbsolutePath());

        if (release) {
            args.add("--release");
        }

        if (allFeatures) {
            args.add("--all-features");
        }

        if (noDefaultFeatures) {
            args.add("--no-default-features");
        }

        if (features != null && features.length > 0) {
            args.add("--features");
            args.add(String.join(",", features));
        }

        if (tests) {
            args.add("--tests");
        }

        if (extraArgs != null) {
            Collections.addAll(args, extraArgs);
        }
        cargo(args);
        copyArtifacts();
    }
}
