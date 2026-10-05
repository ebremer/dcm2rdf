package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.parameters.Parameters;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.media.DicomDirWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests: synthetic DICOM built in memory with dcm4che, run through the real
 * conversion pipeline (traversal, tar handling, RDF writing, output files).
 */
class PipelineTest {

    private static byte[] syntheticDicom(String sopInstanceUID) throws Exception {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        attrs.setString(Tag.PatientID, VR.LO, "42");
        attrs.setString(Tag.PatientName, VR.PN, "Doe^Jane");
        attrs.setString(Tag.StudyDate, VR.DA, "20240115");
        attrs.setString(Tag.StudyTime, VR.TM, "1230");
        attrs.setString(Tag.Modality, VR.CS, "OT");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Attributes fmi = attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        try (DicomOutputStream dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(fmi, attrs);
        }
        return baos.toByteArray();
    }

    private static void addEntry(TarArchiveOutputStream tos, String name, byte[] data) throws Exception {
        addEntry(tos, new TarArchiveEntry(name), data);
    }

    private static void addEntry(TarArchiveOutputStream tos, TarArchiveEntry e, byte[] data) throws Exception {
        e.setSize(data.length);
        tos.putArchiveEntry(e);
        tos.write(data);
        tos.closeArchiveEntry();
    }

    private static Parameters params(Path src, Path dest) {
        Parameters params = new Parameters();
        params.src = src.toFile();
        params.dest = dest.toFile();
        return params;
    }

    private static Model load(Path ttl) throws Exception {
        Model m = ModelFactory.createDefaultModel();
        try (var is = Files.newInputStream(ttl)) {
            RDFDataMgr.read(m, is, Lang.TURTLE);
        }
        return m;
    }

    @Test
    void convertsBytesToModelNamedBySOPInstanceUID() throws Exception {
        DICOM2RDF d2r = new DICOM2RDF(new Parameters());
        Model m = d2r.ProcessDICOMasBytes2Model(Path.of("mem.dcm"), syntheticDicom("1.2.3.4.5"));
        assertFalse(m.isEmpty());
        assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.5")));
    }

    @Test
    void directoryRunWritesTurtleAndRecordsFileHash(@TempDir Path src, @TempDir Path dest) throws Exception {
        byte[] dicom = syntheticDicom("1.2.3.4.100");
        Files.write(src.resolve("img.dcm"), dicom);
        Parameters params = params(src, dest);
        params.hash = true;
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(0, dp.getFileCounter().getFailedConversionFileCount());
        Path out = dest.resolve("img.ttl");
        assertTrue(Files.exists(out), "expected converted output at " + out);
        Model m = load(out);
        assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.100")));
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dicom));
        assertTrue(m.contains(null,
            m.createProperty("http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/", "sha256"),
            expected), "expected recorded sha256 to match the file digest");
    }

    @Test
    void tarAndNestedTarEntriesConvert(@TempDir Path src, @TempDir Path dest) throws Exception {
        ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(innerBytes)) {
            addEntry(tos, "inner1.dcm", syntheticDicom("1.2.3.4.201"));
        }
        ByteArrayOutputStream outerBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(outerBytes)) {
            addEntry(tos, "a/one.dcm", syntheticDicom("1.2.3.4.200"));
            addEntry(tos, "nested.tar", innerBytes.toByteArray());
            addEntry(tos, "notes.txt", "not dicom".getBytes());
        }
        Files.write(src.resolve("archive.tar"), outerBytes.toByteArray());
        Parameters params = params(src, dest);
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(0, dp.getFileCounter().getFailedConversionFileCount());

        Path one = dest.resolve("archive.tar#a").resolve("one.ttl");
        assertTrue(Files.exists(one), "expected tar entry output at " + one);
        assertTrue(load(one).containsResource(load(one).createResource("urn:oid:1.2.3.4.200")));

        Path inner = dest.resolve("archive.tar").resolve("nested.tar#inner1.ttl");
        assertTrue(Files.exists(inner), "expected nested tar entry output at " + inner);
        assertTrue(load(inner).containsResource(load(inner).createResource("urn:oid:1.2.3.4.201")));

        // second run: everything already converted, must complete without failures
        DirectoryProcessor again = new DirectoryProcessor(params);
        again.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(0, again.getFileCounter().getFailedConversionFileCount());
    }

    // cuts into the value of the last element, so parsing hits EOF mid-element
    private static byte[] truncated(byte[] dicom) {
        return java.util.Arrays.copyOf(dicom, dicom.length - 1);
    }

    @Test
    void truncatedFileFailsWithoutWritingOutput(@TempDir Path src, @TempDir Path dest) throws Exception {
        Files.write(src.resolve("cut.dcm"), truncated(syntheticDicom("1.2.3.4.500")));
        Parameters params = params(src, dest);
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(1, dp.getFileCounter().getFailedConversionFileCount());
        assertFalse(Files.exists(dest.resolve("cut.ttl")), "a partial parse must not be written as a conversion");
    }

    @Test
    void badTarEntriesDoNotAbortTheRestOfTheArchive(@TempDir Path src, @TempDir Path dest) throws Exception {
        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(tarBytes)) {
            addEntry(tos, "cut.dcm", truncated(syntheticDicom("1.2.3.4.600")));
            addEntry(tos, "garbage.dcm", "not dicom at all".getBytes());
            addEntry(tos, "good.dcm", syntheticDicom("1.2.3.4.601"));
        }
        Files.write(src.resolve("mixed.tar"), tarBytes.toByteArray());
        Parameters params = params(src, dest);
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);

        assertEquals(2, dp.getFileCounter().getFailedConversionFileCount());
        Path good = dest.resolve("mixed.tar#good.ttl");
        assertTrue(Files.exists(good), "entry after the bad ones must still convert: " + good);
        assertTrue(load(good).containsResource(load(good).createResource("urn:oid:1.2.3.4.601")));
        assertFalse(Files.exists(dest.resolve("mixed.tar#cut.ttl")));
        assertFalse(Files.exists(dest.resolve("mixed.tar#garbage.ttl")));
    }

    @Test
    void dicomdirIsNamedByMediaStorageSOPInstanceUID(@TempDir Path src, @TempDir Path dest) throws Exception {
        // a DICOMDIR dataset carries no (0008,0018); only the file meta (0002,0003) identifies it
        DicomDirWriter.createEmptyDirectory(src.resolve("DICOMDIR").toFile(), "1.2.3.4.700", "FILESET", null, null);
        Parameters params = params(src, dest);
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(0, dp.getFileCounter().getFailedConversionFileCount());
        Path out = dest.resolve("DICOMDIR.ttl");
        assertTrue(Files.exists(out), "expected DICOMDIR output at " + out);
        assertTrue(load(out).containsResource(load(out).createResource("urn:oid:1.2.3.4.700")));
    }

    @Test
    void collidingOutputNamesFailLoudly(@TempDir Path src, @TempDir Path dest) throws Exception {
        // img.dcm and img.dat both map to img.ttl: one converts, the other must not pass silently
        Files.write(src.resolve("img.dcm"), syntheticDicom("1.2.3.4.800"));
        Files.write(src.resolve("img.dat"), syntheticDicom("1.2.3.4.801"));
        Parameters params = params(src, dest);
        params.threads = 4;
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(1, dp.getFileCounter().getFailedConversionFileCount());
        try (var walk = Files.walk(dest)) {
            assertEquals(java.util.List.of(dest.resolve("img.ttl")), walk.filter(Files::isRegularFile).toList());
        }

        // a rerun must report the collision again, not take the existing output as "already done"
        DirectoryProcessor again = new DirectoryProcessor(params);
        again.Protocol(DirectoryProcessor.FileType.DICOM);
        assertEquals(1, again.getFileCounter().getFailedConversionFileCount());
    }

    @Test
    void hostileTarEntriesCannotEscapeDestination(@TempDir Path src, @TempDir Path dest) throws Exception {
        ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(innerBytes)) {
            addEntry(tos, "inner.dcm", syntheticDicom("1.2.3.4.402"));
        }
        ByteArrayOutputStream outerBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(outerBytes)) {
            addEntry(tos, "a/../../../../escape.dcm", syntheticDicom("1.2.3.4.400"));
            // TarArchiveEntry(String) strips leading slashes; a crafted archive keeps them
            addEntry(tos, new TarArchiveEntry("/absolute.dcm", true), syntheticDicom("1.2.3.4.401"));
            addEntry(tos, "../inner.tar", innerBytes.toByteArray());
            addEntry(tos, "safe.dcm", syntheticDicom("1.2.3.4.403"));
        }
        Files.write(src.resolve("evil.tar"), outerBytes.toByteArray());
        Parameters params = params(src, dest);
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.Protocol(DirectoryProcessor.FileType.DICOM);

        // each hostile entry is rejected and counted as failed; the harmless entry still converts
        assertEquals(3, dp.getFileCounter().getFailedConversionFileCount());
        Path safe = dest.resolve("evil.tar#safe.ttl");
        assertTrue(Files.exists(safe), "expected safe entry output at " + safe);
        // where the ".." entry would have landed without the entry-name check
        Path escaped = dest.resolve("evil.tar#a/../../../../escape.ttl").normalize();
        assertFalse(Files.exists(escaped), "tar entry escaped the destination: " + escaped);
        // and inside the destination, the safe output must be the only file written
        try (var walk = Files.walk(dest)) {
            assertEquals(java.util.List.of(safe), walk.filter(Files::isRegularFile).toList());
        }
    }
}
