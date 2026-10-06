package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.DCM;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.jena.rdf.model.Model;
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
 * Tests for the post-conversion optimizations: synthetic DICOM built in memory with
 * dcm4che, converted through Dcm2RdfBuilder.
 */
class PostProcessingTest {

    private static Path dicom(Path dir, String sopInstanceUID, Consumer<Attributes> content) throws Exception {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        attrs.setString(Tag.PatientID, VR.LO, "42");
        content.accept(attrs);
        Path file = dir.resolve("img.dcm");
        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian), attrs);
        }
        return file;
    }

    private static Attributes code(String value) {
        Attributes item = new Attributes();
        item.setString(Tag.CodeValue, VR.SH, value);
        return item;
    }

    private static Property dcm(String local) {
        return ResourceFactory.createProperty(DCM.NS, local);
    }

    private static int listSize(RDFNode node) {
        assertTrue(node.isResource() && node.asResource().hasProperty(RDF.first), "expected an rdf:List, got " + node);
        return node.as(RDFList.class).size();
    }

    @Test
    void sequencesStayListsWhateverTheirItemCount(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, "1.2.3.4.900", a -> {
            a.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("ONE"));
            Sequence two = a.newSequence(Tag.ProcedureCodeSequence, 2);
            two.add(code("A"));
            two.add(code("B"));
        });
        Model m = new Dcm2RdfBuilder().toModel(dcm);
        Resource sop = m.createResource("urn:oid:1.2.3.4.900");
        assertEquals(1, listSize(sop.getRequiredProperty(dcm("0040A043")).getObject()));
        assertEquals(2, listSize(sop.getRequiredProperty(dcm("00081032")).getObject()));
        // attributes that are always single-valued are still unwrapped to their value
        assertEquals("42", sop.getRequiredProperty(dcm("00100020")).getString());
    }

    @Test
    void detlefNamesSequenceItemsAtEveryLevel(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, "1.2.3.4.901", a -> {
            a.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("ONE"));
            Attributes first = code("A");
            Sequence nested = first.newSequence(Tag.ModifierCodeSequence, 2);
            nested.add(code("A1"));
            nested.add(code("A2"));
            Sequence two = a.newSequence(Tag.ProcedureCodeSequence, 2);
            two.add(first);
            two.add(code("B"));
        });
        Model m = new Dcm2RdfBuilder().detlef(true).toModel(dcm);
        String sop = "urn:oid:1.2.3.4.901";
        Map<String, String> expected = Map.of(
            sop + "#0040A043/0", "ONE",
            sop + "#00081032/0", "A",
            sop + "#00081032/1", "B",
            sop + "#00081032/0/0040A195/0", "A1",
            sop + "#00081032/0/0040A195/1", "A2");
        expected.forEach((iri, value) ->
            assertEquals(value, m.createResource(iri).getRequiredProperty(dcm("00080100")).getString(), iri));
        assertFalse(m.listSubjectsWithProperty(dcm("00080100")).toList().stream().anyMatch(RDFNode::isAnon),
            "no sequence item may be left a blank node");
    }

    @Test
    void keywordsKeepRepeatingGroupsApart(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, "1.2.3.4.902", a -> {
            // OverlayRows in two overlay groups share the dictionary keyword
            a.setInt(0x60000010, VR.US, 512);
            a.setInt(0x60020010, VR.US, 256);
        });
        Model m = new Dcm2RdfBuilder().keywords(true).toModel(dcm);
        Resource sop = m.createResource("urn:oid:1.2.3.4.902");
        assertFalse(sop.hasProperty(dcm("OverlayRows")), "overlay groups must not merge onto one keyword");
        assertEquals(512, sop.getRequiredProperty(dcm("60000010")).getInt());
        assertEquals(256, sop.getRequiredProperty(dcm("60020010")).getInt());
        assertTrue(sop.hasProperty(dcm("PatientID")), "other attributes keep their keywords");

        // every overlay group is unwrapped like the 60xx shape says, in hex mode too
        Model hex = new Dcm2RdfBuilder().toModel(dcm);
        assertEquals(256, hex.createResource("urn:oid:1.2.3.4.902").getRequiredProperty(dcm("60020010")).getInt());
    }

    @Test
    void ptagsGroupsPrivateElementsUnderTheirCreator(@TempDir Path dir) throws Exception {
        Path dcm = dicom(dir, "1.2.3.4.904", a -> {
            a.setString(0x00090010, VR.LO, "ACME 1");
            a.setString(0x00091001, VR.LO, "first");
            a.setInt(0x00091002, VR.US, 7);
            a.setString(0x00090011, VR.LO, "ACME 2");
            a.setString(0x00091101, VR.LO, "second");
        });
        Model m = new Dcm2RdfBuilder().ptags(true).toModel(dcm);
        Resource sop = m.createResource("urn:oid:1.2.3.4.904");
        Property hasPrivateElement = dcm("hasPrivateElement");
        Map<String, Resource> blocks = new java.util.HashMap<>();
        sop.listProperties(hasPrivateElement).forEachRemaining(st -> {
            Resource block = st.getResource();
            blocks.put(block.getRequiredProperty(dcm("hasPrivateCreatorId")).getString(), block);
        });
        assertEquals(java.util.Set.of("ACME 1", "ACME 2"), blocks.keySet(), "one block per private creator");

        Resource acme1 = blocks.get("ACME 1");
        assertEquals("0009", acme1.getRequiredProperty(dcm("group")).getString());
        assertEquals("10", acme1.getRequiredProperty(dcm("id")).getString());
        Map<String, Resource> elements = new java.util.HashMap<>();
        acme1.listProperties(dcm("hasElement")).forEachRemaining(st ->
            elements.put(st.getResource().getRequiredProperty(dcm("id")).getString(), st.getResource()));
        assertEquals(java.util.Set.of("01", "02"), elements.keySet());
        assertEquals("first", elements.get("01").getRequiredProperty(DCM.Value).getResource().getRequiredProperty(RDF.first).getString());
        assertEquals("LO", elements.get("01").getRequiredProperty(DCM.vr).getString());
        assertEquals(1, blocks.get("ACME 2").listProperties(dcm("hasElement")).toList().size());

        // the flat form is gone: neither the creators nor their elements hang off the instance
        for (String tag : new String[] {"00090010", "00091001", "00091002", "00090011", "00091101"}) {
            assertFalse(sop.hasProperty(dcm(tag)), tag);
        }
        assertEquals("42", sop.getRequiredProperty(dcm("00100020")).getString(), "standard attributes stay");
    }

    @Test
    void padLeftZeroPadsNumericIdsAndLeavesOthersAlone(@TempDir Path dir) throws Exception {
        String[][] cases = {{"42", "00000042"}, {"0042", "00000042"}, {"AB12", "AB12"}, {"123456789", "123456789"}};
        for (String[] c : cases) {
            Path dcm = dicom(dir, "1.2.3.4.903", a -> a.setString(Tag.PatientID, VR.LO, c[0]));
            Model m = new Dcm2RdfBuilder().padLeftZero(true).toModel(dcm);
            assertEquals(c[1], m.createResource("urn:oid:1.2.3.4.903").getRequiredProperty(dcm("00100020")).getString(), c[0]);
        }
    }
}
