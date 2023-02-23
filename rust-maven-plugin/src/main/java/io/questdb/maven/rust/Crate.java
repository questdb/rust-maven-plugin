package io.questdb.maven.rust;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

/** Scans a Rust crate and extracts information. */
public class Crate {
    private final Path crateRoot;
    private final Path targetDir;
    private final String profile;
    private final CargoToml cargoToml;

    public Crate(Path crateRoot, Path targetDir, String profile) throws MojoExecutionException {
        this.crateRoot = crateRoot;
        this.targetDir = targetDir;
        this.profile = profile;
        cargoToml = CargoToml.parse(this.crateRoot);
    }

    public List<Path> getArtifactPaths() throws MojoExecutionException {
        List<Path> paths = new ArrayList<>();
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
}
