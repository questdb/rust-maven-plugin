package io.questdb.maven.rust;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


public class CrateTest {

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }
    
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private Path targetRootDir;
   
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

        public Path touchSrc(String name) throws IOException {
            final Path srcPath = crateRoot.resolve("src").resolve(name);
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
            expectedBinPath = expectedBinPath.resolve(getPlatformDir());
        }
        expectedBinPath = expectedBinPath.resolve(mockBinPath.getFileName());

        assertTrue(Files.exists(expectedBinPath));
    }

    private String getPlatformDir() {
        final String osName = System.getProperty("os.name");
        final String osArch = System.getProperty("os.arch");
        return (osName + "-" + osArch).toLowerCase().replace(' ', '_');
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
            expectedLibPath = expectedLibPath.resolve(getPlatformDir());
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
    public void testCustomLibName() throws Exception {
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
            .resolve(getPlatformDir())
            .resolve(cdylibPath.getFileName());

        assertTrue(Files.exists(expectedLibPath));
    }
    
    // @Test
    // public void testFindDefaultBinAndLibrary() throws Exception {
    //     ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
    //     TomlTable toml = Toml.parse("[package]\n"
    //             + "name = \"test\"\n"
    //             + "version = \"0.1.0\"\n"
    //             + "edition = \"2021\"");
        
    //     File expectedBin = tempTargetDir.newFile("debug/test");
    //     File expectedLib = tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("test"));
        
    //     assertEquals(Sets.newHashSet(expectedLib, expectedBin), testObject.getArtifacts(toml));
    // }
    
    // @Test
    // public void testFindDefaultBinExplicit() throws Exception {
    //     ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
    //     TomlTable toml = Toml.parse("[package]\n"
    //             + "name = \"test\"\n"
    //             + "version = \"0.1.0\"\n"
    //             + "edition = \"2021\"\n"
    //             + "[[bin]]\n"
    //             + "name = \"test\"\n"
    //             + "src = \"src/main.rs\"");
        
    //     assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
    //     File expected = tempTargetDir.newFile("debug/test");
        
    //     assertEquals(Sets.newHashSet(expected), testObject.getArtifacts(toml));
    // }
    
    // @Test
    // public void testFindMixed() throws Exception {
    //     ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
    //     TomlTable toml = Toml.parse("[package]\n"
    //             + "name = \"test\"\n"
    //             + "version = \"0.1.0\"\n"
    //             + "edition = \"2021\"\n"
    //             + "[lib]\n"
    //             + "name = \"mylib\"\n"
    //             + "[[bin]]\n"
    //             + "name = \"myexe1\"\n"
    //             + "src = \"src/myexe1.rs\"\n"
    //             + "[[bin]]\n"
    //             + "name = \"myexe2\"\n"
    //             + "src = \"src/myexe1.rs\"");
        
    //     assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
    //     File expectedLib = tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("mylib"));
    //     File expectedDefaultBin = tempTargetDir.newFile("debug/test");
    //     File expectedBin1 = tempTargetDir.newFile("debug/myexe1");
    //     File expectedBin2 = tempTargetDir.newFile("debug/myexe2");
        
    //     // do not include default lib if [lib] section specified
    //     tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("test"));
        
    //     assertEquals(Sets.newHashSet(expectedLib, expectedDefaultBin, expectedBin1, expectedBin2), testObject.getArtifacts(toml));
    // }
    
    // @Test
    // public void testFindBadToml() throws Exception {
    //     ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
              
    //     // this should fail upstream, but for completeness test the bad Cargo.toml edge cases
        
    //     // missing [package]
    //     TomlTable toml1 = Toml.parse("name = \"test\"\n"
    //             + "version = \"0.1.0\"\n"
    //             + "edition = \"2021\"");
          
    //     tempTargetDir.newFile("debug/test");
    //     assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml1));
        
    //     // missing name in [[bin]] section
    //     TomlTable toml2 = Toml.parse("[package]\n"
    //             + "name = \"test\"\n"
    //             + "version = \"0.1.0\"\n"
    //             + "edition = \"2021\"\n"
    //             + "[[bin]]\n"
    //             + "src = \"src/myexe.rs\"");
        
    //     tempTargetDir.newFile("debug/myexe");
    //     assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml2));
    // }
    
    // @Test
    // public void testCopyArtifacts() throws Exception {
    //     File copyToDir = tempTargetDir.newFolder("output");
        
    //     ManualOutputRedirector testObject = new ManualOutputRedirector(new SystemStreamLog(), "dummy", true, tempTargetDir.getRoot(), tempTargetDir.getRoot(), copyToDir);
        
    //     File tomlFile = new File(tempTargetDir.getRoot(), "Cargo.toml");
    //     FileUtils.fileWrite(tomlFile, "[package]\n"
    //             + "name = \"test\"\n"
    //             + "version = \"0.1.0\"\n"
    //             + "edition = \"2021\"");
        
    //     tempTargetDir.newFolder("release");
    //     File bin = tempTargetDir.newFile("release/test");
        
    //     File expectedBin = new File(copyToDir, bin.getName());
        
    //     assertFalse(expectedBin.exists());
        
    //     testObject.copyArtifacts();
        
    //     assertEquals(1, copyToDir.listFiles().length);
    //     assertEquals(expectedBin, copyToDir.listFiles()[0]);
    //     assertTrue(expectedBin.exists());
    // }
}
