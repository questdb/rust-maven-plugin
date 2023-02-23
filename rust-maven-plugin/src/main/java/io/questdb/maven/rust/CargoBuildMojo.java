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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * An example of a Maven plugin.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
public class CargoBuildMojo extends AbstractMojo {

    @Parameter(property = "project", readonly = true)
    protected MavenProject project;

    @Parameter(property = "environmentVariables")
    HashMap<String, String> environmentVariables;

    /**
     * Path to the `cargo` command. If unset or set to "cargo", uses $PATH.
     */
    @Parameter(property = "cargoPath", defaultValue = "cargo")
    private String cargoPath;

    /**
     * Path to the Rust crate (or workspace) to build.
     */
    @Parameter(property = "path", required = true)
    private String path;

    private Path cachedPath;

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

    private Path getPath() {
        if (cachedPath == null) {
            cachedPath = Paths.get(path);
            if (!cachedPath.isAbsolute()) {
                cachedPath = project.getBasedir().toPath().resolve(path);
            }
        }
        return cachedPath;
    }

    private String getName() {
        return getPath().getFileName().toString();
    }

    private Path getTargetDir() {
        return Paths.get(
            project.getBuild().getDirectory(),
            "rust-maven-plugin",
            getName());
    }

    private void runCommand(List<String> args)
            throws IOException, InterruptedException, MojoExecutionException {
        final ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putAll(environmentVariables);

        // Set the current working directory for the cargo command.
        processBuilder.directory(getPath().toFile());
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

    private Path getCopyToDir() throws MojoExecutionException {
        if (copyTo == null) {
            return null;
        }

        Path copyToDir = Paths.get(copyTo);
        if (!copyToDir.isAbsolute()) {
            copyToDir = project.getBasedir().toPath().resolve(copyTo);
        }

        if (copyWithPlatformDir) {
            final String osName = System.getProperty("os.name").toLowerCase();
            final String osArch = System.getProperty("os.arch").toLowerCase();
            final String platform = (osName + "-" + osArch).replace(' ', '_');
            copyToDir = copyToDir.resolve(platform);
        }

        if (!Files.exists(copyToDir, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectories(copyToDir);
            } catch (IOException e) {
                throw new MojoExecutionException(
                    "Failed to create directory " + copyToDir +
                    ": " + e.getMessage(), e);
            }
        }

        if (!Files.isDirectory(copyToDir)) {
            throw new MojoExecutionException(copyToDir + " is not a directory");
        }
        return copyToDir;
    }

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        List<String> args = new ArrayList<>();
        args.add("build");

        args.add("--target-dir");
        args.add(getTargetDir().toAbsolutePath().toString());

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
        
        // Cargo nightly has support for `--out-dir`
        // which allows us to copy the artifacts directly to the desired path.
        // Once the feature is stabilized, copy the artifacts directly via:
        // args.add("--out-dir")
        // args.add(getCopyToDir());

        final Crate crate = new Crate(
            getPath(),
            getTargetDir(),
            release ? "release" : "debug");
           
        final Path copyToDir = getCopyToDir();
        if (copyToDir != null) {
            final List<Path> artifactPaths = crate.getArtifactPaths();
            copyArtifacts(copyToDir, artifactPaths);
        }
    }

    private void copyArtifacts(Path copyToDir, List<Path> artifactPaths)
            throws MojoExecutionException {
        Log log = getLog();
        log.info(
            "Copying " + getName() +
            "'s artifacts to " + Shlex.quote(
                copyToDir.toAbsolutePath().toString()));

        for (Path artifactPath : artifactPaths) {
            final Path fileName = artifactPath.getFileName();
            final Path destPath = copyToDir.resolve(fileName);
            try {
                Files.copy(
                    artifactPath, 
                    destPath,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoExecutionException(
                    "Failed to copy " + artifactPath +
                    " to " + copyToDir + ":" + e.getMessage());
            }
            log.info("Copied " + Shlex.quote(fileName.toString()));
        }
    }
}
