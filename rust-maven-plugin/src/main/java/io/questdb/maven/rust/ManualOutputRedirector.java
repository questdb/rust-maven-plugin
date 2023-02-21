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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class ManualOutputRedirector {

    private final Log log;
    private final String name;
    private final boolean release;
    private final File path;
    private final File targetDir;
    private final File copyToDir;

    ManualOutputRedirector(Log log, String name, boolean release, File path, File targetDir, File copyToDir) {
        this.log = log;
        this.name = name;
        this.release = release;
        this.path = path;
        this.targetDir = targetDir;
        this.copyToDir = copyToDir;
    }

    private static TomlTable readCargoToml(File path) throws MojoExecutionException {
        final File tomlFile = new File(path, "Cargo.toml");
        if (!tomlFile.exists()) {
            throw new MojoExecutionException("The <path> arg might be incorrect. Cargo.toml file expected under: " + path);
        }

        try {
            return Toml.parse(tomlFile.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse Cargo.toml file: " + e.getMessage());
        }
    }

    void copyArtifacts() throws MojoExecutionException {
        final TomlTable toml = readCargoToml(path);
        final Set<File> artifacts = getArtifacts(toml);
        for (File artifact : artifacts) {
            final File copyToPath = new File(copyToDir, artifact.getName());
            try {
                Files.copy(artifact.toPath(), copyToPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy " + artifact + " to " + copyToPath + ":" + e.getMessage());
            }
            log.info("Copied " + Shlex.quote(artifact.getName()));
        }
    }

    Set<File> getArtifacts(TomlTable toml) throws MojoExecutionException {
        final String buildType = release ? "release" : "debug";

        TomlTable mainPackage = toml.getTable("package");
        if (mainPackage == null) {
            throw new MojoExecutionException("Malformed Cargo.toml file, expected a [package] section, but was missing.");
        }
        // Split this out and load into a `String name` in `CargoToml`.
        String packageName = mainPackage.getString("name", () -> name);

        final Set<File> artifacts = new HashSet<>();

        // lib.rs or explicit [lib]
        final File libraryPath = getLibraryPath(toml, packageName, buildType);
        if (libraryPath != null) {
            artifacts.add(libraryPath);
        }

        // main.rs
        final File defaultBinPath = new File(targetDir, buildType + "/" + packageName);
        if (defaultBinPath.exists()) {
            artifacts.add(defaultBinPath);
        }

        // other [[bin]]
        final TomlArray bins = toml.getArray(packageName);
        if (bins != null) {
            for (int index = 0; index < bins.size(); ++index) {
                final TomlTable bin = bins.getTable(index);
                final String binName = bin.getString("name");
                if (binName != null) {
                    final File binPath = new File(targetDir, buildType + "/" + binName);
                    if (binPath.exists()) {
                        artifacts.add(binPath);
                    } else {
                        throw new MojoExecutionException("Could not find expected binary: " + Shlex.quote(binPath.getAbsolutePath()));
                    }
                } else {
                    throw new MojoExecutionException("Malformed Cargo.toml file, missing name in [[bin]] section");
                }
            }
        }

        if (artifacts.isEmpty()) {
            throw new MojoExecutionException("Something went wrong. No artifacts produced. We expect a main.rs or specified [lib] or [[bin]] sections");
        }

        return artifacts;
    }

    private File getLibraryPath(TomlTable toml, String packageName, String buildType) throws MojoExecutionException {
        final TomlTable libSection = toml.getTable("lib");

        // TODO: 
        // For now we only support system dynamic libraries (crate_type = cdylib).
        // To make this more general, we need to parse the create_type from the .toml and check for --crate-type extra args.
        // This gets even more complicated when you consider the dynamic create_types like: crate_type = "lib"
        // Hopefully, --out-dir will allow us to avoid all this complexity in the future
        final String libraryName = getOSspecificLibraryName(
                libSection == null ? packageName : libSection.getString("name", () -> packageName));
        final File libraryPath = new File(targetDir, buildType + "/" + libraryName);

        if (libraryPath.exists()) {
            return libraryPath;
        } else {
            if (libSection == null) {
                // assume no [lib] or lib.rs present, which is fine assuming this is a binary only crate
                return null;
            } else {
                // if [lib] section is present then we expect library to be produced
                throw new MojoExecutionException("Could not find expected library: " + Shlex.quote(libraryPath.getAbsolutePath()));
            }
        }
    }

    static String getOSspecificLibraryName(String libName) {
        final String osName = System.getProperty("os.name").toLowerCase();
        final String libPrefix = osName.startsWith("windows") ? "" : "lib";
        final String libSuffix = osName.startsWith("windows")
                ? ".dll" : osName.contains("mac")
                ? ".dylib" : ".so";
        return libPrefix + libName.replace("-", "_") + libSuffix;
    }
}
