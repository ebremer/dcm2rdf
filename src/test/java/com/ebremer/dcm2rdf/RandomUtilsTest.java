package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.DirectoryProcessor.FileType;
import com.ebremer.dcm2rdf.parameters.Parameters;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RandomUtilsTest {

    @Test
    void dumpModelCreatesParentDirsAndLeavesNoTempFile(@TempDir Path dir) throws Exception {
        Model m = ModelFactory.createDefaultModel();
        m.add(m.createResource("urn:test:s"), m.createProperty("urn:test:p"), "o");
        Path out = dir.resolve("sub").resolve("out.ttl");
        RandomUtils.DumpModel(m, out, new Parameters());
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
        assertFalse(Files.exists(out.resolveSibling("out.ttl.tmp")));
    }

    @Test
    void stripExtensionOnlyRemovesTheSuffix() {
        assertEquals("img", RandomUtils.StripExtension("img.dcm"));
        assertEquals("img", RandomUtils.StripExtension("img.dat"));
        assertEquals("dir.dcm/img", RandomUtils.StripExtension("dir.dcm/img.dcm"));
        assertEquals("a.dcm.b", RandomUtils.StripExtension("a.dcm.b"));
        assertEquals("archive.tar#x/y", RandomUtils.StripExtension("archive.tar#x/y.dcm"));
        assertEquals("noext", RandomUtils.StripExtension("noext"));
        assertEquals("DICOMDIR", RandomUtils.StripExtension("DICOMDIR"));
    }

    @Test
    void tarEntryNamesResolveToFileTypes() {
        assertEquals(FileType.DICOM, RandomUtils.getFileType("a/b/c.dcm"));
        assertEquals(FileType.DICOM, RandomUtils.getFileType("a/b/c.DAT"));
        assertEquals(FileType.TAR, RandomUtils.getFileType("a/b/inner.tar"));
        assertEquals(FileType.DICOMDIR, RandomUtils.getFileType("study/DICOMDIR"));
        assertEquals(FileType.DICOMDIR, RandomUtils.getFileType("DICOMDIR"));
        assertEquals(FileType.UNKNOWN, RandomUtils.getFileType("study/dicomdir"));
        assertEquals(FileType.UNKNOWN, RandomUtils.getFileType("study/readme.txt"));
    }
}
