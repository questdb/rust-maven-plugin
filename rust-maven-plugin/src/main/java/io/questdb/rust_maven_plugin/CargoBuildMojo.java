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

/**
 * An example of a Maven plugin.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class CargoBuildMojo extends CargoMojoBase {

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

// TODO: We might need to place the build artifacts inside Maven's build directory.
// If we need to do this then we can use the options below.
//    /**
//     * Directory for all generated artifacts.
//     * Defaults to the Java project's build directory.
//     */
//    @Parameter(property = "target-dir", defaultValue = "${project.build.directory}")
//    private String targetDir;
//
//    @Parameter(property = "default-target-dir", defaultValue = "${project.build.directory}")
//    private boolean defaultTargetDir;

    /**
     * Build all tests.
     * Defaults to "false".
     * Equivalent to Cargo's `--tests` option.
     */
    @Parameter(property = "tests", defaultValue = "false")
    private boolean tests;

    /** Additional args to pass to cargo. */
    @Parameter(property = "extra-args")
    private String[] extraArgs;

    @Override
    public void execute() throws MojoExecutionException {
        List<String> args = new ArrayList<>();
        args.add("build");

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

        if (extraArgs != null && extraArgs.length > 0) {
            for (String arg : extraArgs) {
                args.add(arg);
            }
        }
        cargo(args);
    }
}
