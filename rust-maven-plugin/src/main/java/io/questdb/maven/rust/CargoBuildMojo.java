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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

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

    private Path getCrateRoot() {
        Path crateRoot = Paths.get(path);
        if (!crateRoot.isAbsolute()) {
            crateRoot = project.getBasedir().toPath().resolve(path);
        }
        return crateRoot;
    }

    @Override
    public void execute() throws MojoExecutionException {
        final Crate.Params params = extractCrateParams();
        final Path targetRootDir = Paths.get(
            project.getBuild().getDirectory(),
            "rust-maven-plugin");

        final Crate crate = new Crate(getCrateRoot(), targetRootDir, params);
        crate.setLog(getLog());
        crate.build();
        crate.copyArtifacts();
    }

    private Crate.Params extractCrateParams() {
        final Crate.Params params = new Crate.Params();
        params.environmentVariables = environmentVariables;
        params.cargoPath = cargoPath;
        params.release = release;
        params.features = features;
        params.allFeatures = allFeatures;
        params.noDefaultFeatures = noDefaultFeatures;
        params.tests = tests;
        params.extraArgs = extraArgs;
        if (copyTo != null) {
            Path copyToDir = Paths.get(copyTo);
            if (!copyToDir.isAbsolute()) {
                copyToDir = project.getBasedir().toPath()
                    .resolve(copyToDir);
            }
            params.copyToDir = copyToDir;
        }
        params.copyWithPlatformDir = copyWithPlatformDir;
        return params;
    }
}
