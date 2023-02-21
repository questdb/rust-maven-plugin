package io.questdb.maven.rust;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

import com.google.common.collect.Sets;

public class ManualOutputRedirectorTest {
    
    @Rule
    public TemporaryFolder tempTargetDir = new TemporaryFolder();
    
    @Before
    public void before() throws IOException {
        tempTargetDir.newFolder("debug");
    }
    
    @Test
    public void testFindDefaultBin() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
        TomlTable toml = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"");
           
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
        File expected = tempTargetDir.newFile("debug/test");
        
        assertEquals(Sets.newHashSet(expected), testObject.getArtifacts(toml));
    }
    
    @Test
    public void testFindDefaultLib() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
        TomlTable toml = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"");
           
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
        File expected = tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("test"));
        
        assertEquals(Sets.newHashSet(expected), testObject.getArtifacts(toml));
    }
    
    @Test
    public void testFindDefaultLibWithName() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
        TomlTable toml = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"\n"
                + "[lib]\n"
                + "name = \"mylib\"");
           
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
        File expected = tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("mylib"));
        
        assertEquals(Sets.newHashSet(expected), testObject.getArtifacts(toml));
    }
    
    @Test
    public void testFindDefaultBinAndLibrary() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
        TomlTable toml = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"");
        
        File expectedBin = tempTargetDir.newFile("debug/test");
        File expectedLib = tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("test"));
        
        assertEquals(Sets.newHashSet(expectedLib, expectedBin), testObject.getArtifacts(toml));
    }
    
    @Test
    public void testFindDefaultBinExplicit() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
        TomlTable toml = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"\n"
                + "[[bin]]\n"
                + "name = \"test\"\n"
                + "src = \"src/main.rs\"");
        
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
        File expected = tempTargetDir.newFile("debug/test");
        
        assertEquals(Sets.newHashSet(expected), testObject.getArtifacts(toml));
    }
    
    @Test
    public void testFindMixed() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
                
        TomlTable toml = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"\n"
                + "[lib]\n"
                + "name = \"mylib\"\n"
                + "[[bin]]\n"
                + "name = \"myexe1\"\n"
                + "src = \"src/myexe1.rs\"\n"
                + "[[bin]]\n"
                + "name = \"myexe2\"\n"
                + "src = \"src/myexe1.rs\"");
        
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml));
        
        File expectedLib = tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("mylib"));
        File expectedDefaultBin = tempTargetDir.newFile("debug/test");
        File expectedBin1 = tempTargetDir.newFile("debug/myexe1");
        File expectedBin2 = tempTargetDir.newFile("debug/myexe2");
        
        // do not include default lib if [lib] section specified
        tempTargetDir.newFile("debug/" + ManualOutputRedirector.getOSspecificLibraryName("test"));
        
        assertEquals(Sets.newHashSet(expectedLib, expectedDefaultBin, expectedBin1, expectedBin2), testObject.getArtifacts(toml));
    }
    
    @Test
    public void testFindBadToml() throws Exception {
        ManualOutputRedirector testObject = new ManualOutputRedirector(null, "dummy", false, null, tempTargetDir.getRoot(), null);
              
        // this should fail upstream, but for completeness test the bad Cargo.toml edge cases
        
        // missing [package]
        TomlTable toml1 = Toml.parse("name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"");
          
        tempTargetDir.newFile("debug/test");
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml1));
        
        // missing name in [[bin]] section
        TomlTable toml2 = Toml.parse("[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"\n"
                + "[[bin]]\n"
                + "src = \"src/myexe.rs\"");
        
        tempTargetDir.newFile("debug/myexe");
        assertThrows(MojoExecutionException.class, () -> testObject.getArtifacts(toml2));
    }
    
    @Test
    public void testCopyArtifacts() throws Exception {
        File copyToDir = tempTargetDir.newFolder("output");
        
        ManualOutputRedirector testObject = new ManualOutputRedirector(new SystemStreamLog(), "dummy", true, tempTargetDir.getRoot(), tempTargetDir.getRoot(), copyToDir);
        
        File tomlFile = new File(tempTargetDir.getRoot(), "Cargo.toml");
        FileUtils.fileWrite(tomlFile, "[package]\n"
                + "name = \"test\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"");
        
        tempTargetDir.newFolder("release");
        File bin = tempTargetDir.newFile("release/test");
        
        File expectedBin = new File(copyToDir, bin.getName());
        
        assertFalse(expectedBin.exists());
        
        testObject.copyArtifacts();
        
        assertEquals(1, copyToDir.listFiles().length);
        assertEquals(expectedBin, copyToDir.listFiles()[0]);
        assertTrue(expectedBin.exists());
    }
}
