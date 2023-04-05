package io.questdb.maven.rust;

import io.questdb.jar.jni.Platform;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;


public class CrateTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private Path targetRootDir;

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    @Before
    public void before() throws IOException {
        tmpDir = new TemporaryFolder();
        tmpDir.create();
        targetRootDir = tmpDir.newFolder(
                "target",
                "rust-maven-plugin").toPath();
    }

    @After
    public void after() {
        tmpDir.delete();
    }

    private void doTestDefaultBin(
            boolean release,
            boolean copyTo,
            boolean copyWithPlatformDir) throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test-bin-1",
                release ? "release" : "debug");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"test-bin\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n");
        mock.touchSrc("main.rs");
        final Path mockBinPath = mock.touchBin("test-bin");
        assertTrue(Files.exists(mockBinPath));

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = release;
        if (copyTo) {
            params.copyToDir = tmpDir.newFolder("bin_dest_dir").toPath();
        }
        params.copyWithPlatformDir = copyWithPlatformDir;

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(1, artifacts.size());
        assertEquals(mockBinPath, artifacts.get(0));

        if (!copyTo) {
            return;
        }

        crate.copyArtifacts();

        Path expectedBinPath = params.copyToDir;
        if (copyWithPlatformDir) {
            expectedBinPath = expectedBinPath.resolve(
                    Platform.RESOURCE_PREFIX);
        }
        expectedBinPath = expectedBinPath.resolve(mockBinPath.getFileName());

        assertTrue(Files.exists(expectedBinPath));
    }

    public void testDefaultBinDebugNoCopyTo() throws Exception {
        doTestDefaultBin(false, false, false);
    }

    public void testDefaultBinReleaseNoCopyTo() throws Exception {
        // Last arg to `true` should be ignored.
        doTestDefaultBin(true, false, true);
    }

    @Test
    public void testDefaultBinDebugCopyTo() throws Exception {
        doTestDefaultBin(false, true, false);
    }

    @Test
    public void testDefaultBinReleaseCopyTo() throws Exception {
        doTestDefaultBin(true, true, false);
    }

    @Test
    public void testDefaultBinDebugCopyToPlatformDir() throws Exception {
        doTestDefaultBin(false, true, true);
    }

    @Test
    public void testDefaultBinReleaseCopyToPlatformDir() throws Exception {
        doTestDefaultBin(true, true, true);
    }

    private void doTestCdylib(boolean release, boolean copyTo, boolean copyWithPlatformDir) throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test-lib-1",
                release ? "release" : "debug");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"test-lib\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n" +
                        "\n" +
                        "[lib]\n" +
                        "crate-type = [\"cdylib\"]\n");
        mock.touchSrc("lib.rs");
        final Path cdylibPath = mock.touchLib("test-lib");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = release;
        if (copyTo) {
            params.copyToDir = tmpDir.newFolder("lib_dest_dir").toPath();
        }
        params.copyWithPlatformDir = copyWithPlatformDir;

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(1, artifacts.size());
        assertEquals(cdylibPath, artifacts.get(0));

        if (!copyTo) {
            return;
        }

        crate.copyArtifacts();

        Path expectedLibPath = params.copyToDir;
        if (copyWithPlatformDir) {
            expectedLibPath = expectedLibPath.resolve(Platform.RESOURCE_PREFIX);
        }
        expectedLibPath = expectedLibPath.resolve(cdylibPath.getFileName());

        assertTrue(Files.exists(expectedLibPath));
    }

    @Test
    public void testCdylibDebug() throws Exception {
        // Last arg to `true` should be ignored.
        doTestCdylib(false, false, true);
    }

    @Test
    public void testCdylibDebugCopyTo() throws Exception {
        doTestCdylib(false, true, false);
    }

    @Test
    public void testCdylibReleaseCopyTo() throws Exception {
        doTestCdylib(true, true, false);
    }

    @Test
    public void testCdylibDebugCopyToPlatformDir() throws Exception {
        doTestCdylib(false, true, true);
    }

    @Test
    public void testCdylibReleaseCopyToPlatformDir() throws Exception {
        doTestCdylib(true, true, true);
    }

    @Test
    public void testCustomCdylibName() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test-lib-1",
                "debug");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"test-lib\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n" +
                        "\n" +
                        "[lib]\n" +
                        "name = \"mylib\"\n" +
                        "crate-type = [\"cdylib\"]\n");
        mock.touchSrc("lib.rs");
        final Path cdylibPath = mock.touchLib("mylib");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = false;
        params.copyToDir = tmpDir.newFolder("lib_dest_dir").toPath();
        params.copyWithPlatformDir = true;

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(1, artifacts.size());
        assertEquals(cdylibPath, artifacts.get(0));

        crate.copyArtifacts();

        Path expectedLibPath = params.copyToDir
                .resolve(Platform.RESOURCE_PREFIX)
                .resolve(cdylibPath.getFileName());

        assertTrue(Files.exists(expectedLibPath));
    }

    @Test
    public void testCdylibLegacyKeyCrateType() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test-lib-1",
                "debug");
        mock.writeCargoToml(
                "[package]\n" +
                "name = \"test-lib\"\n" +
                "version = \"0.1.0\"\n" +
                "edition = \"2021\"\n" +
                "\n" +
                "[lib]\n" +
                "crate_type = [\"cdylib\"]\n");
        mock.touchSrc("lib.rs");
        final Path cdylibPath = mock.touchLib("test-lib");

        // Configuring the build job.

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                new Crate.Params());

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();

        assertEquals(1, artifacts.size());
        assertEquals(cdylibPath, artifacts.get(0));
    }

    @Test
    public void testDefaultBinAndCdylib() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test42",
                "debug");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"test42\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n" +
                        "\n" +
                        "[lib]\n" +
                        "crate-type = [\"cdylib\"]\n");
        mock.touchSrc("lib.rs");
        mock.touchSrc("main.rs");
        final Path cdylibPath = mock.touchLib("test42");
        final Path binPath = mock.touchBin("test42");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = false;
        params.copyToDir = tmpDir.newFolder("dest_dir").toPath();

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(2, artifacts.size());
        assertEquals(cdylibPath, artifacts.get(0));
        assertEquals(binPath, artifacts.get(1));

        crate.copyArtifacts();

        Path expectedLibPath = params.copyToDir
                .resolve(cdylibPath.getFileName());
        Path expectedBinPath = params.copyToDir
                .resolve(binPath.getFileName());

        assertTrue(Files.exists(expectedLibPath));
        assertTrue(Files.exists(expectedBinPath));
    }

    @Test
    public void testRenamedDefaultBin() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test-custom-name-bin",
                "debug");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"test-custom-name-bin\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n" +
                        "\n" +
                        "[[bin]]\n" +
                        "name = \"test43\"\n" +
                        "path = \"src/main.rs\"\n");
        mock.touchSrc("main.rs");
        final Path binPath = mock.touchBin("test43");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = false;
        params.copyToDir = tmpDir.newFolder("dest_dir").toPath();

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(1, artifacts.size());
        assertEquals(binPath, artifacts.get(0));

        crate.copyArtifacts();

        Path expectedBinPath = params.copyToDir
                .resolve(binPath.getFileName());

        assertTrue(Files.exists(expectedBinPath));
    }

    @Test
    public void testConfiguredDefaultBin() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate(
                "test-configured-bin",
                "debug");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"test-configured-bin\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n" +
                        "\n" +
                        "[[bin]]\n" +
                        "name = \"test-configured-bin\"\n" +
                        "path = \"src/main.rs\"\n");
        mock.touchSrc("main.rs");
        final Path binPath = mock.touchBin("test-configured-bin");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = false;
        params.copyToDir = tmpDir.newFolder("dest_dir").toPath();

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(1, artifacts.size());
        assertEquals(binPath, artifacts.get(0));

        crate.copyArtifacts();

        Path expectedBinPath = params.copyToDir
                .resolve(binPath.getFileName());

        assertTrue(Files.exists(expectedBinPath));
    }

    @Test
    public void testCdylibDefaultBinAndExplicitBin() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate("mixed", "release");
        mock.writeCargoToml(
                "[package]\n" +
                        "name = \"mixed\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n" +
                        "\n" +
                        "[lib]\n" +
                        "crate-type = [\"cdylib\"]\n" +
                        "\n" +
                        "[[bin]]\n" +
                        "name = \"extra-bin\"\n" +
                        "path = \"src/extra-bin/main.rs\"\n");
        mock.touchSrc("lib.rs");
        mock.touchSrc("main.rs");
        mock.touchSrc("extra-bin", "main.rs");

        final Path cdylibPath = mock.touchLib("mixed");
        final Path binPath = mock.touchBin("mixed");
        final Path extraBinPath = mock.touchBin("extra-bin");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = true;
        params.copyToDir = tmpDir.newFolder("dest_dir").toPath();

        final Crate crate = new Crate(
                mock.crateRoot,
                targetRootDir,
                params);

        // Checking the expected paths it generates.
        final List<Path> artifacts = crate.getArtifactPaths();
        assertEquals(3, artifacts.size());
        assertEquals(cdylibPath, artifacts.get(0));
        assertEquals(binPath, artifacts.get(1));
        assertEquals(extraBinPath, artifacts.get(2));

        crate.copyArtifacts();

        Path expectedLibPath = params.copyToDir
                .resolve(cdylibPath.getFileName());
        Path expectedBinPath = params.copyToDir
                .resolve(binPath.getFileName());
        Path expectedExtraBinPath = params.copyToDir
                .resolve(extraBinPath.getFileName());

        assertTrue(Files.exists(expectedLibPath));
        assertTrue(Files.exists(expectedBinPath));
        assertTrue(Files.exists(expectedExtraBinPath));
    }

    @Test
    public void testBadCargoToml() throws Exception {
        // Setting up mock Rust project directory.
        final MockCrate mock = new MockCrate("bad-toml", "release");
        mock.writeCargoToml(
                //  "[package]\n" +   MISSING!
                "name = \"bad-toml\"\n" +
                        "version = \"0.1.0\"\n" +
                        "edition = \"2021\"\n");
        mock.touchSrc("main.rs");
        mock.touchBin("bad-toml");

        // Configuring the build job.
        final Crate.Params params = new Crate.Params();
        params.release = true;
        params.copyToDir = tmpDir.newFolder("dest_dir").toPath();

        assertThrows(
                MojoExecutionException.class,
                () -> new Crate(mock.crateRoot, targetRootDir, params));
    }

    class MockCrate {
        private final String name;
        private final String profile;
        private final Path crateRoot;

        public MockCrate(String name, String profile) throws IOException {
            this.name = name;
            this.profile = profile;
            this.crateRoot = tmpDir.newFolder(name).toPath();
        }

        public void writeCargoToml(String contents) throws IOException {
            Path cargoToml = crateRoot.resolve("Cargo.toml");
            try (PrintWriter w = new PrintWriter(cargoToml.toFile(), "UTF-8")) {
                w.write(contents);
            }
        }

        public Path touchBin(String name) throws IOException {
            final Path mockBinPath = targetRootDir
                    .resolve(this.name)
                    .resolve(profile)
                    .resolve(name + (isWindows() ? ".exe" : ""));
            if (!Files.exists(mockBinPath.getParent())) {
                Files.createDirectories(mockBinPath.getParent());
            }
            Files.createFile(mockBinPath);
            return mockBinPath;
        }

        public Path touchSrc(String... pathComponents) throws IOException {
            Path srcPath = crateRoot.resolve("src");
            for (String pathComponent : pathComponents) {
                srcPath = srcPath.resolve(pathComponent);
            }
            if (!Files.exists(srcPath.getParent())) {
                Files.createDirectories(srcPath.getParent());
            }
            Files.createFile(srcPath);
            return srcPath;
        }

        public Path touchLib(String name) throws IOException {
            final String prefix = isWindows() ? "" : "lib";
            final String suffix =
                    isWindows() ? ".dll" :
                            isMac() ? ".dylib"
                                    : ".so";
            final String libName = prefix + name.replace('-', '_') + suffix;
            final Path libPath = targetRootDir
                    .resolve(this.name)
                    .resolve(profile)
                    .resolve(libName);
            if (!Files.exists(libPath.getParent())) {
                Files.createDirectories(libPath.getParent());
            }
            Files.createFile(libPath);
            return libPath;
        }
    }
}
