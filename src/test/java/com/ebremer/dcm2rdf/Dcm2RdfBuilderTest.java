package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.ns.LOC;
import com.ebremer.dcm2rdf.ns.PROVO;
import java.io.ByteArrayOutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the embedded-library entry point: synthetic DICOM built in memory with
 * dcm4che, converted through Dcm2RdfBuilder from files, paths, and channels.
 */
class Dcm2RdfBuilderTest {

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

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @Test
    void convertsPath(@TempDir Path dir) throws Exception {
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, syntheticDicom("1.2.3.4.10"));
        Model m = new Dcm2RdfBuilder().toModel(dcm);
        assertFalse(m.isEmpty());
        assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.10")));
    }

    @Test
    void convertsFile(@TempDir Path dir) throws Exception {
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, syntheticDicom("1.2.3.4.11"));
        Model m = new Dcm2RdfBuilder().toModel(dcm.toFile());
        assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.11")));
    }

    @Test
    void convertsInMemoryChannelAndLeavesItOpen() throws Exception {
        try (SeekableInMemoryByteChannel sbc = new SeekableInMemoryByteChannel(syntheticDicom("1.2.3.4.12"))) {
            Model m = new Dcm2RdfBuilder().toModel(Path.of("mem.dcm"), "mem.dcm", sbc);
            assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.12")));
            assertTrue(sbc.isOpen(), "builder must not close the caller's channel");
        }
    }

    @Test
    void convertsFileBackedChannel(@TempDir Path dir) throws Exception {
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, syntheticDicom("1.2.3.4.13"));
        try (SeekableByteChannel sbc = Files.newByteChannel(dcm, StandardOpenOption.READ)) {
            Model m = new Dcm2RdfBuilder().toModel(dcm, dcm.toString(), sbc);
            assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.13")));
        }
    }

    @Test
    void sha256NamingNamesSubjectByDigest() throws Exception {
        byte[] dicom = syntheticDicom("1.2.3.4.14");
        try (SeekableInMemoryByteChannel sbc = new SeekableInMemoryByteChannel(dicom)) {
            Model m = new Dcm2RdfBuilder().naming(Dcm2RdfBuilder.Naming.SHA256)
                .toModel(Path.of("mem.dcm"), "mem.dcm", sbc);
            assertTrue(m.containsResource(m.createResource("urn:sha256:" + sha256(dicom))));
        }
    }

    @Test
    void hashRecordsDigestLiteral(@TempDir Path dir) throws Exception {
        byte[] dicom = syntheticDicom("1.2.3.4.15");
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, dicom);
        Model m = new Dcm2RdfBuilder().hash(true).toModel(dcm);
        assertTrue(m.contains(null, LOC.cryptographicHashFunctions.sha256, sha256(dicom)));
    }

    @Test
    void keywordsUseKeywordPredicates(@TempDir Path dir) throws Exception {
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, syntheticDicom("1.2.3.4.16"));
        Model m = new Dcm2RdfBuilder().keywords(true).toModel(dcm);
        Resource subject = m.createResource("urn:oid:1.2.3.4.16");
        assertTrue(subject.hasProperty(m.createProperty(DCM.NS, "PatientID")));
    }

    @Test
    void extraRecordsSourceUriAndFileSize(@TempDir Path dir) throws Exception {
        byte[] dicom = syntheticDicom("1.2.3.4.17");
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, dicom);
        Model m = new Dcm2RdfBuilder().extra(true).toModel(dcm);
        Resource subject = m.createResource("urn:oid:1.2.3.4.17");
        assertTrue(m.contains(subject, PROVO.wasDerivedFrom, m.createResource(dcm.toUri().toString())));
        assertTrue(m.contains(subject, LOC.BibFrame.FileSize,
            ResourceFactory.createTypedLiteral(String.valueOf(dicom.length), XSDDatatype.XSDinteger)));
    }

    @Test
    void extraViaChannelRecordsNamedSource(@TempDir Path dir) throws Exception {
        byte[] dicom = syntheticDicom("1.2.3.4.18");
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, dicom);
        try (SeekableByteChannel sbc = Files.newByteChannel(dcm, StandardOpenOption.READ)) {
            Model m = new Dcm2RdfBuilder().extra(true).toModel(dcm, dcm.toString(), sbc);
            Resource subject = m.createResource("urn:oid:1.2.3.4.18");
            assertTrue(m.contains(subject, PROVO.wasDerivedFrom, m.createResource(dcm.toUri().toString())));
            assertTrue(m.contains(subject, LOC.BibFrame.FileSize,
                ResourceFactory.createTypedLiteral(String.valueOf(dicom.length), XSDDatatype.XSDinteger)));
        }
    }

    @Test
    void longFormSkipsOptimizations(@TempDir Path dir) throws Exception {
        Path dcm = dir.resolve("img.dcm");
        Files.write(dcm, syntheticDicom("1.2.3.4.19"));
        Model m = new Dcm2RdfBuilder().longForm(true).toModel(dcm);
        // long form keeps the vr/Value element structure the optimizations would collapse
        assertTrue(m.contains(null, DCM.vr), "expected dcm:vr nodes to survive in long form");
    }

    @Test
    void builderIsReusable(@TempDir Path dir) throws Exception {
        Dcm2RdfBuilder builder = new Dcm2RdfBuilder();
        for (int i = 0; i < 2; i++) {
            Path dcm = dir.resolve("img" + i + ".dcm");
            Files.write(dcm, syntheticDicom("1.2.3.4.2" + i));
            Model m = builder.toModel(dcm);
            assertTrue(m.containsResource(m.createResource("urn:oid:1.2.3.4.2" + i)));
        }
    }
}
