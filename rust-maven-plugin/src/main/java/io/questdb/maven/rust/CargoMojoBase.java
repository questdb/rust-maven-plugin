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
        return Paths.get(
                project.getBuild().getDirectory(),
                "rust-maven-plugin");
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
