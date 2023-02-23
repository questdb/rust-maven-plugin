package io.questdb.maven.rust;

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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/** Controls running tasks on a Rust crate. */
public class Crate {
    public static class Params {
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
    }

    private Log log;
    private final Path crateRoot;
    private final Path targetDir;
    private final CargoToml cargoToml;
    private final Params params;

    public Crate(
            Path crateRoot,
            Path targetRootDir,
            Params params) throws MojoExecutionException {
        this.log = nullLog();
        this.crateRoot = crateRoot;
        this.targetDir = targetRootDir.resolve(getDirName());
        this.cargoToml = CargoToml.parse(this.crateRoot);
        this.params = params;
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

    public List<Path> getArtifactPaths() throws MojoExecutionException {
        List<Path> paths = new ArrayList<>();
        final String profile = getProfile();

        final String libName = cargoToml.getLibName();
        if (libName != null) {
            final Path libPath = targetDir
                .resolve(profile)
                .resolve(pinLibName(libName));
            paths.add(libPath);
        }

        final List<String> binNames = cargoToml.getBinNames();
        if (binNames != null) {
            for (String binName : binNames) {
                final Path binPath = targetDir
                    .resolve(profile)
                    .resolve(pinBinName(binName));
                paths.add(binPath);
            }
        }

        return paths;
    }

    public static String pinLibName(String name) {
        final String osName = System.getProperty("os.name").toLowerCase();
        final String libPrefix = osName.startsWith("windows") ? "" : "lib";
        final String libSuffix = osName.startsWith("windows")
                ? ".dll" : osName.contains("mac")
                ? ".dylib" : ".so";
        return libPrefix + name.replace("-", "_") + libSuffix;
    }

    public static String pinBinName(String name) {
        final String osName = System.getProperty("os.name").toLowerCase();
        final String binSuffix = osName.startsWith("windows") ? ".exe" : "";
        return name + binSuffix;
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

    private void cargo(List<String> args) throws MojoExecutionException {
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
            throw new MojoExecutionException("Failed to invoke cargo", e);
        }
    }

    public void build() throws MojoExecutionException {
        List<String> args = new ArrayList<>();
        args.add("build");

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
            args.add(String.join(",", params.features));
        }

        if (params.tests) {
            args.add("--tests");
        }

        if (params.extraArgs != null) {
            Collections.addAll(args, params.extraArgs);
        }

        cargo(args);
    }

    private Path resolveCopyToDir() throws MojoExecutionException {

        Path copyToDir = params.copyToDir;

        if (copyToDir == null) {
            return null;
        }

        if (params.copyWithPlatformDir) {
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
}
