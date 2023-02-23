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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/** Parses the `Cargo.toml` file. */
public class CargoToml {

    /** A cell for a potentially cached (memoized) value. */
    private static class CacheBox<T> {
        private T value;

        public CacheBox(T value) {
            this.value = value;
        }

        public static <T> CacheBox<T> of(T value) {
            return new CacheBox<>(value);
        }

        public T get() {
            return value;
        }
    }

    private final TomlTable toml;

    // Nulls for unextracted values.
    // Internal value may still be `null` if not present in the file.
    private CacheBox<String> packageName;
    private CacheBox<String> libName;
    private CacheBox<List<String>> binNames;

    private CargoToml(TomlTable toml) {
        this.toml = toml;
        this.packageName = null;
        this.libName = null;
        this.binNames = null;
    }

    private String getPackageNameImpl() throws MojoExecutionException {
        try {
            String name = toml.getString("package.name");
            if (name == null) {
                throw new MojoExecutionException(
                    "Missing required `package.name` from Cargo.toml file");
            }
            return name;
        } catch (TomlInvalidTypeException e) {
            throw new MojoExecutionException(
                "Failed to extract `package.name` from Cargo.toml file: " +
                e.getMessage());
        }
    }

    public String getPackageName() throws MojoExecutionException {
        if (packageName == null) {
            packageName = CacheBox.of(getPackageNameImpl());
        }
        return packageName.get();
    }

    public boolean hasLib() {
        return toml.contains("lib");
    }

    private String getLibNameImpl() throws MojoExecutionException {
        String name = null;
        try {
            name = toml.getString("lib.name");
        }
        catch (TomlInvalidTypeException e) {
            throw new MojoExecutionException(
                "Failed to extract `lib.name` from Cargo.toml file: " +
                e.getMessage());
        }

        // The name might be missing, but the lib section might be present.
        if ((name == null) && hasLib()) {
            name = getPackageName();
        }

        return name;
    }

    public String getLibName() throws MojoExecutionException {
        if (libName == null) {
            libName = CacheBox.of(getLibNameImpl());
        }
        return libName.get();
    }

    private List<String> getBinNamesImpl() throws MojoExecutionException {
        final List<String> binNames = new java.util.ArrayList<>();

        String defaultBin = null;
        if (!hasLib()) {
            // Expecting default bin, given that there's no lib.
            defaultBin = getPackageName();
            binNames.add(defaultBin);
        }

        TomlArray bins;
        try {
            bins = toml.getArray("bin");
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

            // This `[[bin]]` entry just configures the default bin.
            // It's already been added.
            if (!name.equals(defaultBin)) {
                binNames.add(name);
            }
        }

        return binNames;
    }

    public List<String> getBinNames() throws MojoExecutionException {
        if (binNames == null) {
            binNames = CacheBox.of(getBinNamesImpl());
        }
        return binNames.get();
    }

    static CargoToml parse(Path cratePath) throws MojoExecutionException {
        final Path tomlPath = cratePath.resolve("Cargo.toml");
        
        if (!Files.exists(tomlPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new MojoExecutionException(
                "Cargo.toml file expected under: " + cratePath);
        }

        try {
            return new CargoToml(Toml.parse(tomlPath));
        } catch (IOException e) {
            throw new MojoExecutionException(
                "Failed to parse Cargo.toml file: " + e.getMessage());
        }
    }
}
