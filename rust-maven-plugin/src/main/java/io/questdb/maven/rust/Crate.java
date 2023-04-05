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

import io.questdb.jar.jni.Platform;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Controls running tasks on a Rust crate.
 */
public class Crate {
    private final Path crateRoot;
    private final Path targetDir;
    private final Params params;
    private final TomlTable cargoToml;
    private final String packageName;
    private Log log;

    public Crate(
            Path crateRoot,
            Path targetRootDir,
            Params params) throws MojoExecutionException {
        this.log = nullLog();
        this.crateRoot = crateRoot;
        this.targetDir = targetRootDir.resolve(getDirName());
        this.params = params;

        final Path tomlPath = crateRoot.resolve("Cargo.toml");
        if (!Files.exists(tomlPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new MojoExecutionException(
                    "Cargo.toml file expected under: " + crateRoot);
        }
        try {
            this.cargoToml = Toml.parse(tomlPath);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to parse Cargo.toml file: " + e.getMessage());
        }

        try {
            packageName = cargoToml.getString("package.name");
            if (packageName == null) {
                throw new MojoExecutionException(
                        "Missing required `package.name` from Cargo.toml file");
            }
        } catch (TomlInvalidTypeException e) {
            throw new MojoExecutionException(
                    "Failed to extract `package.name` from Cargo.toml file: " +
                            e.getMessage());
        }
    }

    public static String pinLibName(String name) {
        return Platform.LIB_PREFIX +
                name.replace('-', '_') +
                Platform.LIB_SUFFIX;
    }

    public static String pinBinName(String name) {
        return name + Platform.EXE_SUFFIX;
    }

    public static Log nullLog() {
        return new Log() {
            @Override
            public void debug(CharSequence content) {
            }

            @Override
            public void debug(CharSequence content, Throwable error) {
            }

            @Override
            public void debug(Throwable error) {
            }

            @Override
            public void error(CharSequence content) {
            }

            @Override
            public void error(CharSequence content, Throwable error) {
            }

            @Override
            public void error(Throwable error) {
            }

            @Override
            public void info(CharSequence content) {
            }

            @Override
            public void info(CharSequence content, Throwable error) {
            }

            @Override
            public void info(Throwable error) {
            }

            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public boolean isErrorEnabled() {
                return false;
            }

            @Override
            public boolean isInfoEnabled() {
                return false;
            }

            @Override
            public boolean isWarnEnabled() {
                return false;
            }

            @Override
            public void warn(CharSequence content) {
            }

            @Override
            public void warn(CharSequence content, Throwable error) {
            }

            @Override
            public void warn(Throwable error) {
            }
        };
    }

    public void setLog(Log log) {
        this.log = log;
    }

    private String getDirName() {
        return crateRoot.getFileName().toString();
    }

    private String getProfile() {
        return params.release ? "release" : "debug";
    }

    public boolean hasCdylib() {
        try {
            TomlArray crateTypes = getCrateTypes();
            if (crateTypes == null) return false;

            for (int index = 0; index < crateTypes.size(); index++) {
                String crateType = crateTypes.getString(index);
                if ((crateType != null) && crateType.equals("cdylib")) {
                    return true;
                }
            }

            return false;
        } catch (TomlInvalidTypeException e) {
            return false;
        }
    }

    private TomlArray getCrateTypes() {
        TomlArray crateTypes = cargoToml.getArray("lib.crate-type");
        if (crateTypes == null) {
            String crateTypeLegacyKey = "lib.crate_type";
            return cargoToml.getArray(crateTypeLegacyKey);
        }
        return crateTypes;
    }

    private String getCdylibName() throws MojoExecutionException {
        String name = null;
        try {
            name = cargoToml.getString("lib.name");
        } catch (TomlInvalidTypeException e) {
            throw new MojoExecutionException(
                    "Failed to extract `lib.name` from Cargo.toml file: " +
                            e.getMessage());
        }

        // The name might be missing, but the lib section might be present.
        if ((name == null) && hasCdylib()) {
            name = packageName;
        }

        return name;
    }

    private List<String> getBinNames() throws MojoExecutionException {
        final List<String> binNames = new java.util.ArrayList<>();

        String defaultBin = null;
        if (Files.exists(crateRoot.resolve("src").resolve("main.rs"))) {
            // Expecting default bin, given that there's no lib.
            defaultBin = packageName;
            binNames.add(defaultBin);
        }

        TomlArray bins;
        try {
            bins = cargoToml.getArray("bin");
        } catch (TomlInvalidTypeException e) {
            throw new MojoExecutionException(
                    "Failed to extract `bin`s from Cargo.toml file: " +
                            e.getMessage());
        }

        if (bins == null) {
            return binNames;
        }

        for (int index = 0; index < bins.size(); ++index) {
            final TomlTable bin = bins.getTable(index);
            if (bin == null) {
                throw new MojoExecutionException(
                        "Failed to extract `bin`s from Cargo.toml file: " +
                                "expected a `bin` table at index " + index);
            }

            String name = null;
            try {
                name = bin.getString("name");
            } catch (TomlInvalidTypeException e) {
                throw new MojoExecutionException(
                        "Failed to extract `bin`s from Cargo.toml file: " +
                                "expected a string at index " + index + " `name` key");
            }

            if (name == null) {
                throw new MojoExecutionException(
                        "Failed to extract `bin`s from Cargo.toml file: " +
                                "missing `name` key at `bin` with index " + index);
            }

            String path = null;
            try {
                path = bin.getString("path");
            } catch (TomlInvalidTypeException e) {
                throw new MojoExecutionException(
                        "Failed to extract `bin`s from Cargo.toml file: " +
                                "expected a string at index " + index + " `path` key");
            }

            // Handle special case where the default bin is renamed.
            if ((path != null) && path.equals("src/main.rs")) {
                defaultBin = name;
                binNames.remove(0);
                binNames.add(0, defaultBin);
            }

            // This `[[bin]]` entry just configures the default bin.
            // It's already been added.
            if (!name.equals(defaultBin)) {
                binNames.add(name);
            }
        }

        return binNames;
    }

    public List<Path> getArtifactPaths() throws MojoExecutionException {
        List<Path> paths = new ArrayList<>();
        final String profile = getProfile();

        final String libName = getCdylibName();
        if (libName != null) {
            final Path libPath = targetDir
                    .resolve(profile)
                    .resolve(pinLibName(libName));
            paths.add(libPath);
        }

        for (String binName : getBinNames()) {
            final Path binPath = targetDir
                    .resolve(profile)
                    .resolve(pinBinName(binName));
            paths.add(binPath);
        }

        return paths;
    }

    private String getCargoPath() {
        String path = params.cargoPath;

        final boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        // Expand "~" to user's home directory.
        // This works around a limitation of ProcessBuilder.
        if (!isWindows && path.startsWith("~/")) {
            path = System.getProperty("user.home") + path.substring(1);
        }

        return path;
    }

    private void runCommand(List<String> args)
            throws IOException, InterruptedException, MojoExecutionException {
        final ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putAll(params.environmentVariables);

        // Set the current working directory for the cargo command.
        processBuilder.directory(crateRoot.toFile());
        final Process process = processBuilder.start();
        Executors.newSingleThreadExecutor().submit(() -> {
            new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines()
                    .forEach(log::info);
        });

        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new MojoExecutionException(
                    "Cargo command failed with exit code " + exitCode);
        }
    }

    private void cargo(List<String> args) throws MojoExecutionException, MojoFailureException {
        String cargoPath = getCargoPath();
        final List<String> cmd = new ArrayList<>();
        cmd.add(cargoPath);
        cmd.addAll(args);
        log.info("Working directory: " + crateRoot);
        if (!params.environmentVariables.isEmpty()) {
            log.info("Environment variables:");
            for (String key : params.environmentVariables.keySet()) {
                log.info("  " + key + "=" + Shlex.quote(
                        params.environmentVariables.get(key)));
            }
        }
        log.info("Running: " + Shlex.quote(cmd));
        try {
            runCommand(cmd);
        } catch (IOException | InterruptedException e) {
            CargoInstalledChecker.INSTANCE.check(log, cargoPath);
            throw new MojoFailureException("Failed to invoke cargo", e);
        }
    }

    private void addCargoArgs(List<String> args) {
        if (params.verbosity != null) {
            args.add(params.verbosity);
        }

        args.add("--target-dir");
        args.add(targetDir.toAbsolutePath().toString());

        if (params.release) {
            args.add("--release");
        }

        if (params.allFeatures) {
            args.add("--all-features");
        }

        if (params.noDefaultFeatures) {
            args.add("--no-default-features");
        }

        if (params.features != null && params.features.length > 0) {
            args.add("--features");
            args.add(String.join(",", params.cleanedFeatures()));
        }

        if (params.tests) {
            args.add("--tests");
        }

        if (params.extraArgs != null) {
            Collections.addAll(args, params.extraArgs);
        }
    }

    public void build() throws MojoExecutionException, MojoFailureException {
        List<String> args = new ArrayList<>();
        args.add("build");
        addCargoArgs(args);
        cargo(args);
    }

    public void test() throws MojoExecutionException, MojoFailureException {
        List<String> args = new ArrayList<>();
        args.add("test");
        addCargoArgs(args);
        cargo(args);
    }

    private Path resolveCopyToDir() throws MojoExecutionException {

        Path copyToDir = params.copyToDir;

        if (copyToDir == null) {
            return null;
        }

        if (params.copyWithPlatformDir) {
            copyToDir = copyToDir.resolve(Platform.RESOURCE_PREFIX);
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

    public void copyArtifacts() throws MojoExecutionException {
        // Cargo nightly has support for `--out-dir`
        // which allows us to copy the artifacts directly to the desired path.
        // Once the feature is stabilized, copy the artifacts directly via:
        // args.add("--out-dir")
        // args.add(resolveCopyToDir());
        final Path copyToDir = resolveCopyToDir();
        if (copyToDir == null) {
            return;
        }
        final List<Path> artifactPaths = getArtifactPaths();
        log.info(
                "Copying " + getDirName() +
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

    public static class Params {
        public String verbosity;
        public HashMap<String, String> environmentVariables;
        public String cargoPath;
        public boolean release;
        public String[] features;
        public boolean allFeatures;
        public boolean noDefaultFeatures;
        public boolean tests;
        public String[] extraArgs;
        public Path copyToDir;
        public boolean copyWithPlatformDir;

        /**
         * Returns the features array with empty and null elements removed.
         */
        public String[] cleanedFeatures() {
            List<String> cleanedFeatures = new ArrayList<>();
            for (String feature : features) {
                if (feature != null) {
                    feature = feature.trim();
                    if (!feature.isEmpty()) {
                        cleanedFeatures.add(feature);
                    }
                }
            }
            return cleanedFeatures.toArray(new String[0]);
        }
    }
}
