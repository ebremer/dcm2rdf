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
        TarArchiveEntry e = new TarArchiveEntry(name);
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
}
