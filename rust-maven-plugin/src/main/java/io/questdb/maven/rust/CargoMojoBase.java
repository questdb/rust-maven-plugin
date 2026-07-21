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
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;


public abstract class CargoMojoBase extends AbstractMojo {
    @Parameter(property = "project", readonly = true)
    protected MavenProject project;

    /**
     * Skip this configured execution.
     */
    @Parameter(property = "skip", defaultValue = "false")
    protected boolean skip;

    /**
     * Additional environment variables set when running `cargo`.
     */
    @Parameter(property = "environmentVariables")
    private HashMap<String, String> environmentVariables;

    /**
     * Path to the `cargo` command. If unset or set to "cargo", uses $PATH.
     */
    @Parameter(property = "cargoPath", defaultValue = "cargo")
    private String cargoPath;

    /**
     * Path to the Rust crate to build.
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
     * Set the verbosity level, forwarded to Cargo.
     * Valid values are "", "-q", "-v", "-vv".
     */
    @Parameter(property = "verbosity")
    private String verbosity;

    /**
     * Additional args to pass to cargo.
     */
    @Parameter(property = "extra-args")
    private String[] extraArgs;

    /**
     * Root directory for cargo's build output, passed to cargo as the
     * `--target-dir` argument (with the crate's directory name appended).
     * <p>
     * Defaults to `${project.build.directory}/rust-maven-plugin`, which keeps the
     * Rust build output inside Maven's `target` directory (and therefore cleaned by
     * `mvn clean`).
     * <p>
     * Point multiple projects - or multiple git worktrees of the same project - at a
     * single shared directory to reuse compiled dependencies across builds instead of
     * recompiling and re-storing the same crates for each checkout. When a directory is
     * shared, the plugin serializes builds against it with its own lock (see
     * `TargetDirLock`) so concurrent plugin-driven builds cannot overwrite each other's
     * artifacts. That lock does not coordinate with raw `cargo` runs outside the plugin,
     * so do not point a concurrent standalone `cargo` build at a shared directory.
     * Note that a shared directory set outside `${project.build.directory}` is no longer
     * removed by `mvn clean`.
     */
    @Parameter(
            property = "targetRootDir",
            defaultValue = "${project.build.directory}/rust-maven-plugin")
    private String targetRootDir;

    protected String getVerbosity() throws MojoExecutionException {
        if (verbosity == null) {
            return null;
        }
        switch (verbosity) {
            case "":
                return null;
            case "-q":
            case "-v":
            case "-vv":
                return verbosity;
            default:
                throw new MojoExecutionException("Invalid verbosity: " + verbosity);
        }
    }

    protected Path getCrateRoot() {
        Path crateRoot = Paths.get(path);
        if (!crateRoot.isAbsolute()) {
            crateRoot = project.getBasedir().toPath().resolve(path);
        }
        return crateRoot;
    }

    protected Path getTargetRootDir() {
        if ((targetRootDir == null) || targetRootDir.trim().isEmpty()) {
            return Paths.get(
                    project.getBuild().getDirectory(),
                    "rust-maven-plugin");
        }
        return Paths.get(targetRootDir);
    }

    /**
     * Whether the configured target root is a shared directory, i.e. not the module's
     * own directory under `${project.build.directory}`. The build lock only engages for
     * shared directories, so a conventional single-checkout build is unaffected.
     */
    protected boolean isSharedTargetDir() {
        final Path root = getTargetRootDir().toAbsolutePath().normalize();
        final Path buildDir = Paths.get(project.getBuild().getDirectory())
                .toAbsolutePath().normalize();
        return !root.startsWith(buildDir);
    }

    protected Crate.Params getCommonCrateParams() throws MojoExecutionException {
        final Crate.Params params = new Crate.Params();
        params.verbosity = getVerbosity();
        params.environmentVariables = environmentVariables;
        params.cargoPath = cargoPath;
        params.release = release;
        params.features = features;
        params.allFeatures = allFeatures;
        params.noDefaultFeatures = noDefaultFeatures;
        params.extraArgs = extraArgs;
        return params;
    }
}
