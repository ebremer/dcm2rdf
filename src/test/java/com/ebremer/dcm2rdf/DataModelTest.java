package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.ns.GEO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How values are represented in the output: synthetic DICOM built in memory with dcm4che,
 * converted through Dcm2RdfBuilder.
 */
class DataModelTest {

    private static final String SOP = "1.2.3.4.950";

    private static Path dicom(Path dir, Consumer<Attributes> content) throws Exception {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, SOP);
        content.accept(attrs);
        Attributes fmi = Attributes.createFileMetaInformation(
            attrs.getString(Tag.SOPInstanceUID, SOP), UID.SecondaryCaptureImageStorage, UID.ExplicitVRLittleEndian);
        Path file = dir.resolve("img.dcm");
        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(fmi, attrs);
        }
        return file;
    }

    private static Property dcm(String local) {
        return ResourceFactory.createProperty(DCM.NS, local);
    }

    private static RDFNode value(Model m, String local) {
        return m.createResource("urn:oid:" + SOP).getRequiredProperty(dcm(local)).getObject();
    }

    private static List<RDFNode> list(RDFNode node) {
        assertTrue(node.isResource() && node.asResource().hasProperty(RDF.first), "expected an rdf:List, got " + node);
        return node.as(RDFList.class).asJavaList();
    }

    private static void assertLiteral(RDFNode node, String lexical, String datatype) {
        assertTrue(node.isLiteral(), "expected a literal, got " + node);
        assertEquals(lexical, node.asLiteral().getLexicalForm());
        assertEquals(datatype, node.asLiteral().getDatatypeURI());
    }

    @Test
    void temporalValuesKeepTheirPrecision(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> {
            a.setString(Tag.StudyDate, VR.DA, "20240115");
            a.setString(Tag.StudyTime, VR.TM, "123045.5");
            a.setString(Tag.AcquisitionDateTime, VR.DT, "20240115123045.123456+0500");
            a.setString(Tag.FrameAcquisitionDateTime, VR.DT, "202401");
        });
        Model m = new Dcm2RdfBuilder().toModel(dcm);
        assertLiteral(value(m, "00080020"), "2024-01-15", XSDDatatype.XSDdate.getURI());
        assertLiteral(value(m, "00080030"), "12:30:45.5", XSDDatatype.XSDtime.getURI());
        assertLiteral(value(m, "0008002A"), "2024-01-15T12:30:45.123456+05:00", XSDDatatype.XSDdateTime.getURI());
        assertLiteral(value(m, "00189074"), "2024-01", XSDDatatype.XSDgYearMonth.getURI());
    }

    @Test
    void invalidValuesAreTypedPerVrAndFlagTheInstance(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> {
            a.setString(Tag.SliceThickness, VR.DS, "1.0.0");
            a.setString(Tag.ContentDate, VR.DA, "20241315");
            a.setString(Tag.InstanceNumber, VR.IS, "1.5");
            a.setString(Tag.AcquisitionDateTime, VR.DT, "2024133");
            a.setString(Tag.SeriesTime, VR.TM, "2561");
        });
        Model m = new Dcm2RdfBuilder().toModel(dcm);
        assertLiteral(value(m, "00180050"), "1.0.0", DCM.NS + "invalidDS");
        assertLiteral(value(m, "00080023"), "20241315", DCM.NS + "invalidDA");
        assertLiteral(value(m, "00200013"), "1.5", DCM.NS + "invalidIS");
        assertLiteral(value(m, "0008002A"), "2024133", DCM.NS + "invalidDT");
        assertLiteral(value(m, "00080031"), "2561", DCM.NS + "invalidTM");
        assertTrue(value(m, "invalidSOPInstance").asLiteral().getBoolean());

        Model valid = new Dcm2RdfBuilder().toModel(dicom(dir, a -> a.setString(Tag.ContentDate, VR.DA, "20241215")));
        assertFalse(valid.contains(null, DCM.invalidSOPInstance), "a valid file is not flagged");
    }

    @Test
    void invalidValuesAreLoggedOncePerFileAsAWarning(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> {
            a.setString(Tag.SliceThickness, VR.DS, "1.0.0");
            a.setString(Tag.ContentDate, VR.DA, "20241315");
            a.setString(Tag.SeriesTime, VR.TM, "2561");
        });
        Logger logger = Logger.getLogger(RDFWriter.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord r) {
                records.add(r);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Level level = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(capture);
        try {
            new Dcm2RdfBuilder().toModel(dcm);
        } finally {
            logger.removeHandler(capture);
            logger.setLevel(level);
        }
        assertEquals(3, records.stream().filter(r -> r.getLevel() == Level.FINE).count(), "each value, at FINE");
        assertEquals(1, records.stream().filter(r -> r.getLevel() == Level.WARNING).count(), "the file, once");
        assertTrue(records.stream().noneMatch(r -> r.getLevel() == Level.SEVERE));
    }

    @Test
    void nonFiniteFloatsUseTheXsdForms(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> a.setDouble(Tag.DiffusionGradientOrientation, VR.FD,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY));
        List<RDFNode> values = list(value(new Dcm2RdfBuilder().toModel(dcm), "00189089"));
        String fd = XSDDatatype.XSDdouble.getURI();
        assertLiteral(values.get(0), "NaN", fd);
        assertLiteral(values.get(1), "INF", fd);
        assertLiteral(values.get(2), "-INF", fd);
    }

    @Test
    void omittedBinaryValuesAreMarkedWithTheirSize(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> a.setBytes(0x60003000, VR.OW, new byte[8]));
        Model m = new Dcm2RdfBuilder().toModel(dcm);
        // inline binary (the file meta version, 2 bytes) without -includeinlinebinary
        Resource version = value(m, "00020001").asResource();
        assertFalse(version.hasProperty(DCM.InlineBinary), "no empty InlineBinary claiming the value is empty");
        assertEquals(2, version.getRequiredProperty(DCM.InlineBinaryOmitted).getInt());
        // bulk data (an overlay plane) is never converted, but no longer vanishes
        Resource overlay = value(m, "60003000").asResource();
        assertEquals("OW", overlay.getRequiredProperty(DCM.vr).getString());
        assertEquals(8, overlay.getRequiredProperty(DCM.BulkDataOmitted).getInt());

        Model included = new Dcm2RdfBuilder().includeInlineBinary(true).toModel(dcm);
        assertEquals("AAE=", value(included, "00020001").asResource().getRequiredProperty(DCM.InlineBinary).getString());
    }

    @Test
    void oidLeavesUidsThatAreNotOidsAsLiterals(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> {
            a.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.951");
            a.setString(Tag.FrameOfReferenceUID, VR.UI, "1.2.abc");
        });
        Model m = new Dcm2RdfBuilder().oid(true).toModel(dcm);
        assertEquals(m.createResource("urn:oid:1.2.3.4.951"), value(m, "0020000D"));
        assertLiteral(value(m, "00200052"), "1.2.abc", XSDDatatype.XSDstring.getURI());
    }

    @Test
    void aSopInstanceUidThatIsNotAnOidCannotNameTheInstance(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, a -> a.setString(Tag.SOPInstanceUID, VR.UI, "1.2.abc"));
        assertThrows(IllegalStateException.class, () -> new Dcm2RdfBuilder().toModel(dcm));
        Model hashed = new Dcm2RdfBuilder().naming(Dcm2RdfBuilder.Naming.SHA256).toModel(dcm);
        assertFalse(hashed.isEmpty());
    }

    @Test
    void namingIgnoresSopInstanceUidsInsideSequenceItems(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("img.dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        Attributes item = new Attributes();
        item.setString(Tag.SOPInstanceUID, VR.UI, "9.9.9");
        attrs.newSequence(Tag.ReferencedImageSequence, 1).add(item);
        Attributes fmi = Attributes.createFileMetaInformation("1.2.3.4.952", UID.SecondaryCaptureImageStorage, UID.ExplicitVRLittleEndian);
        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(fmi, attrs);
        }
        Model m = new Dcm2RdfBuilder().toModel(file);
        // no (0008,0018) of its own: the file meta UID names it, not the item's
        assertTrue(m.contains(m.createResource("urn:oid:1.2.3.4.952"), RDF.type, DCM.SOPInstance));
        assertFalse(m.containsResource(m.createResource("urn:oid:9.9.9")));
    }

    @Test
    void cdtLeavesListsWithBlankMembersAlone() {
        // a blank member (here a dcm:Null) would be written into the literal as a bare blank node label
        Model m = ModelFactory.createDefaultModel();
        Resource sop = m.createResource("urn:oid:" + SOP);
        sop.addProperty(dcm("00080008"), m.createList(m.createLiteral("ORIGINAL"),
            m.createResource().addProperty(RDF.type, DCM.Null), m.createLiteral("AXIAL")));
        sop.addProperty(dcm("00200037"), m.createList(m.createTypedLiteral("1", XSDDatatype.XSDdecimal),
            m.createTypedLiteral("0", XSDDatatype.XSDdecimal)));
        com.ebremer.dcm2rdf.parameters.Parameters params = new com.ebremer.dcm2rdf.parameters.Parameters();
        params.cdtlevel = 2;
        new DICOM2RDF(params).listsToCdt(m);
        assertEquals(3, list(value(m, "00080008")).size());
        assertEquals("http://w3id.org/awslabs/neptune/SPARQL-CDTs/List", value(m, "00200037").asLiteral().getDatatypeURI());
    }

    @Test
    void wktConvertsEveryContourTypeAndLeavesMalformedOnesAlone(@TempDir Path dir) throws Exception {
        String[][] contours = {
            {"POINT", "5", "5", "0"},
            {"OPEN_PLANAR", "0", "0", "0", "10", "0", "0"},
            {"CLOSED_PLANAR", "0", "0", "0", "10", "0", "0", "10", "10", "0"},
            {"CLOSED_PLANAR", "0", "0", "0", "10"}};
        Path dcm = dicom(dir, a -> {
            Sequence seq = a.newSequence(Tag.ContourSequence, contours.length);
            for (String[] c : contours) {
                Attributes contour = new Attributes();
                contour.setString(Tag.ContourGeometricType, VR.CS, c[0]);
                contour.setString(Tag.ContourData, VR.DS, java.util.Arrays.copyOfRange(c, 1, c.length));
                seq.add(contour);
            }
        });
        Model m = new Dcm2RdfBuilder().wkt(true).toModel(dcm);
        List<RDFNode> items = list(value(m, "30060040"));
        String[] expected = {"POINT Z(0.005 0.005 0)", "LINESTRING Z(0 0 0, 0.01 0 0)", "POLYGON Z((0 0 0, 0.01 0 0, 0.01 0.01 0, 0 0 0))"};
        for (int i = 0; i < expected.length; i++) {
            Literal wkt = items.get(i).asResource().getRequiredProperty(dcm("30060050")).getLiteral();
            assertEquals(GEO.NS + "wktLiteral", wkt.getDatatypeURI());
            assertEquals("<http://www.opengis.net/def/crs/EPSG/0/7706> " + expected[i], wkt.getLexicalForm().replace("Z (", "Z("));
        }
        assertEquals(4, list(items.get(3).asResource().getRequiredProperty(dcm("30060050")).getObject()).size(),
            "4 values are not (x, y, z) triplets: kept as the list");
    }
}
