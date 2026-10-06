package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.DirectoryProcessor.FileType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceFilesTest {

    @Test
    void stripExtensionOnlyRemovesTheSuffix() {
        assertEquals("img", SourceFiles.stripExtension("img.dcm"));
        assertEquals("img", SourceFiles.stripExtension("img.dat"));
        assertEquals("dir.dcm/img", SourceFiles.stripExtension("dir.dcm/img.dcm"));
        assertEquals("a.dcm.b", SourceFiles.stripExtension("a.dcm.b"));
        assertEquals("archive.tar#x/y", SourceFiles.stripExtension("archive.tar#x/y.dcm"));
        assertEquals("noext", SourceFiles.stripExtension("noext"));
        assertEquals("DICOMDIR", SourceFiles.stripExtension("DICOMDIR"));
    }

    @Test
    void tarEntryNamesResolveToFileTypes() {
        assertEquals(FileType.DICOM, SourceFiles.getFileType("a/b/c.dcm"));
        assertEquals(FileType.DICOM, SourceFiles.getFileType("a/b/c.DAT"));
        assertEquals(FileType.TAR, SourceFiles.getFileType("a/b/inner.tar"));
        assertEquals(FileType.DICOMDIR, SourceFiles.getFileType("study/DICOMDIR"));
        assertEquals(FileType.DICOMDIR, SourceFiles.getFileType("DICOMDIR"));
        assertEquals(FileType.UNKNOWN, SourceFiles.getFileType("study/dicomdir"));
        assertEquals(FileType.UNKNOWN, SourceFiles.getFileType("study/readme.txt"));
    }
}
