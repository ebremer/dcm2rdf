package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.ns.GEO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real DICOM samples, written by other software than dcm4che, converted and checked: what each one
 * covers, conformance to the shapes under every option, and a digest of the default output.
 * <p>
 * The samples are third-party data, so they are not stored in the repository: with the
 * {@code golden} profile ({@code mvn test -Pgolden}) they are downloaded into
 * {@code target/golden-samples} and checked against pinned hashes. Without it these tests are
 * skipped. Where the samples come from, and their licences, is in {@code golden/README.md}.
 * <p>
 * The expected output is kept as a digest ({@code golden/expected-digests.properties}), so no
 * patient data is committed either. After a change to the output that is meant, record the new
 * digests with {@code mvn test -Pgolden -Dgolden.update=true}. That also saves each output as
 * {@code target/golden-samples/<name>.accepted.ttl}, against which a later mismatch is reported.
 */
@EnabledIfSystemProperty(named = "golden", matches = "true")
class GoldenFileTest {

    /** An element to cut out of a sample after download: tag and VR, explicit VR, 2-byte length. */
    record Strip(int tag, String vr) {}

    /**
     * A sample: where it is, the SHA-256 of what is served there, and, when elements are cut out of
     * it, the SHA-256 of the result.
     */
    record Sample(String name, String uid, String url, String sha256, boolean bigEndian, List<Strip> strip, String strippedSha256) {}

    private static final Sample RTSTRUCT = new Sample("tcia-sts-017-rtstruct",
        "1.3.6.1.4.1.14519.5.2.1.5168.1900.307075493446128985917276226872",
        "https://idc-open-data.s3.amazonaws.com/cd506b5c-a9b1-4349-b651-1e05fc188028/d7394a95-781a-4425-b2e4-dd48b4d7de17.dcm",
        "39fd6445059adaf0ba60f07f2285a09eb8eeb1eeebe4aa96b851f518542e0da2",
        false, List.of(), null);

    // (0009,100E), GE's "Scan Ready Datetime", holds an unset timestamp that TCIA's date shift
    // moved: it reveals the shift, and with it the real scan date
    private static final Sample PET = new Sample("tcia-cptac-lscc-pet-bigendian",
        "1.3.6.1.4.1.14519.5.2.1.4801.5885.172166061721650680329868080402",
        "https://idc-open-data-two.s3.amazonaws.com/752a1df1-1164-41da-87b6-a67d47dd9dce/98ed8478-f042-4c55-9eda-006573ee6b7f.dcm",
        "a08154d7149e2096ac450714425738828928f9720099e5add57265e10225c1f6",
        true, List.of(new Strip(0x0009100E, "DT")),
        "c019a30181a2d1249e62e3d7144e35e92ec9c1765949cd467575c895790866f3");

    private static final Path SAMPLES = Path.of("target/golden-samples");
    private static final Path DIGESTS = Path.of("src/test/resources/golden/expected-digests.properties");
    private static final Shapes SHAPES = Shapes.parse(RDFDataMgr.loadGraph(
        GoldenFileTest.class.getResource("/shacl.ttl").toString(), Lang.TURTLE));

    static List<Named<Sample>> samples() {
        return Stream.of(RTSTRUCT, PET).map(s -> Named.of(s.name(), s)).toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // the sample, downloaded once, checked against its pinned hash, and stripped
    private static synchronized Path fetch(Sample s) throws Exception {
        Path file = SAMPLES.resolve(s.name() + ".dcm");
        String expected = s.strippedSha256() != null ? s.strippedSha256() : s.sha256();
        if (Files.exists(file) && sha256(Files.readAllBytes(file)).equals(expected)) {
            return file;
        }
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
        HttpResponse<byte[]> response = http.send(
            HttpRequest.newBuilder(URI.create(s.url())).timeout(Duration.ofMinutes(2)).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), "downloading " + s.url());
        byte[] bytes = response.body();
        assertEquals(s.sha256(), sha256(bytes), s.url() + " no longer serves the pinned file");
        for (Strip strip : s.strip()) {
            bytes = strip(bytes, strip, s.bigEndian());
        }
        assertEquals(expected, sha256(bytes), s.name() + " stripped");
        Files.createDirectories(SAMPLES);
        Files.write(file, bytes);
        return file;
    }

    // Cuts a top-level element out of the file, found by its tag and VR. Only for an element whose
    // length nothing else counts; the stripped file's pinned hash checks the result.
    static byte[] strip(byte[] file, Strip strip, boolean bigEndian) {
        assertTrue(Set.of("AE", "AS", "AT", "CS", "DA", "DS", "DT", "FD", "FL", "IS", "LO", "LT", "PN", "SH", "SL", "SS", "ST", "TM", "UI", "UL", "US")
            .contains(strip.vr()), strip.vr() + " has a 4-byte length");
        byte[] header = ByteBuffer.allocate(6).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
            .putShort((short) (strip.tag() >>> 16)).putShort((short) strip.tag())
            .put(strip.vr().getBytes(StandardCharsets.US_ASCII)).array();
        int at = -1;
        for (int i = 0; i + header.length <= file.length; i++) {
            if (Arrays.equals(file, i, i + header.length, header, 0, header.length)) {
                assertEquals(-1, at, "more than one match for the element to strip");
                at = i;
            }
        }
        assertNotEquals(-1, at, "no element to strip");
        int length = bigEndian
            ? (file[at + 6] & 0xff) << 8 | (file[at + 7] & 0xff)
            : (file[at + 7] & 0xff) << 8 | (file[at + 6] & 0xff);
        int end = at + 8 + length;
        byte[] out = new byte[file.length - (end - at)];
        System.arraycopy(file, 0, out, 0, at);
        System.arraycopy(file, end, out, at, file.length - end);
        return out;
    }

    /**
     * A digest of a graph that is the same for every copy of it, whatever its blank node labels:
     * a blank node stands for the digest of what hangs off it. That names blank nodes alike in
     * isomorphic graphs as long as they form trees, as the converter's do (sequence items, value
     * nodes, list cells); a cycle through blank nodes fails.
     */
    static String digest(Model m) {
        List<String> lines = canonical(m).stream().map(Triple::toString).sorted().toList();
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    // a triple with its blank nodes named by digest
    private record Triple(String s, String p, String o) {
        @Override
        public String toString() {
            return s + " <" + p + "> " + o;
        }
    }

    private static List<Triple> canonical(Model m) {
        Map<Resource, String> blanks = new HashMap<>();
        List<Triple> triples = new ArrayList<>();
        m.listStatements().forEachRemaining(st -> triples.add(
            new Triple(term(st.getSubject(), blanks), st.getPredicate().getURI(), term(st.getObject(), blanks))));
        return triples;
    }

    private static String term(RDFNode n, Map<Resource, String> blanks) {
        if (n.isURIResource()) {
            return "<" + n.asResource().getURI() + ">";
        }
        if (n.isLiteral()) {
            Literal l = n.asLiteral();
            String lexical = l.getLexicalForm().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            return "\"" + lexical + "\"^^<" + l.getDatatypeURI() + ">@" + l.getLanguage();
        }
        return blank(n.asResource(), blanks);
    }

    // post-order without recursion: a contour's list runs thousands of cells deep
    private static String blank(Resource root, Map<Resource, String> blanks) {
        Deque<Resource> stack = new ArrayDeque<>();
        Set<Resource> expanded = new HashSet<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Resource b = stack.peek();
            if (blanks.containsKey(b)) {
                stack.pop();
                continue;
            }
            List<Resource> pending = b.listProperties().mapWith(Statement::getObject)
                .filterKeep(o -> o.isAnon() && !blanks.containsKey(o.asResource())).mapWith(RDFNode::asResource).toList();
            if (pending.isEmpty()) {
                stack.pop();
                List<String> parts = new ArrayList<>();
                b.listProperties().forEachRemaining(st -> parts.add("<" + st.getPredicate().getURI() + "> " + term(st.getObject(), blanks)));
                Collections.sort(parts);
                blanks.put(b, "_:" + sha256(String.join("\n", parts).getBytes(StandardCharsets.UTF_8)));
            } else {
                assertTrue(expanded.add(b), "a cycle through blank nodes");
                pending.forEach(stack::push);
            }
        }
        return blanks.get(root);
    }

    private static Map<String, String> readDigests() throws IOException {
        Properties p = new Properties();
        if (Files.exists(DIGESTS)) {
            try (InputStream in = Files.newInputStream(DIGESTS)) {
                p.load(in);
            }
        }
        Map<String, String> digests = new TreeMap<>();
        p.forEach((k, v) -> digests.put((String) k, (String) v));
        return digests;
    }

    private static synchronized void writeDigest(String name, String digest) throws IOException {
        Map<String, String> digests = readDigests();
        digests.put(name, digest);
        StringBuilder sb = new StringBuilder("""
            # SHA-256 digests of the RDF each golden sample converts to with the default options, from
            # GoldenFileTest.digest. Rewritten by: mvn test -Pgolden -Dgolden.update=true
            """);
        digests.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        Files.writeString(DIGESTS, sb.toString());
    }

    private static void write(Model m, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            RDFDataMgr.write(out, m, RDFFormat.TURTLE_PRETTY);
        }
    }

    // The predicates of the triples in only one of the graphs, compared with blank nodes named by
    // digest: the attributes that changed and, for a change inside a sequence item, the sequences
    // above it and the item's other attributes (a changed item has a new name). Without the
    // values, which are patient data.
    private static Set<String> differingPredicates(Model a, Model b) {
        Map<Triple, Integer> count = new HashMap<>();
        canonical(a).forEach(t -> count.merge(t, 1, Integer::sum));
        canonical(b).forEach(t -> count.merge(t, -1, Integer::sum));
        Set<String> predicates = new TreeSet<>();
        count.forEach((t, n) -> {
            if (n != 0) {
                predicates.add(t.p());
            }
        });
        return predicates;
    }

    private static Property dcm(String local) {
        return ResourceFactory.createProperty(DCM.NS, local);
    }

    // the first item of one of r's sequences
    private static Resource firstItem(Resource r, String tag) {
        return r.getRequiredProperty(dcm(tag)).getObject().as(RDFList.class).get(0).asResource();
    }

    private static void assertConforms(Model m, String what) {
        ValidationReport report = ShaclValidator.get().validate(SHAPES, m.getGraph());
        if (!report.conforms()) {
            ShLib.printReport(report);
        }
        assertTrue(report.conforms(), what + " does not conform to shacl.ttl");
    }

    @ParameterizedTest
    @MethodSource("samples")
    void convertsAsBefore(Sample sample) throws Exception {
        Model actual = new Dcm2RdfBuilder().toModel(fetch(sample));
        String digest = digest(actual);
        Path accepted = SAMPLES.resolve(sample.name() + ".accepted.ttl");
        if (Boolean.getBoolean("golden.update")) {
            writeDigest(sample.name(), digest);
            write(actual, accepted);
        }
        String expected = readDigests().get(sample.name());
        assertNotNull(expected, "no digest for " + sample.name() + " in " + DIGESTS + "; run with -Dgolden.update=true");
        if (!digest.equals(expected)) {
            Path actualFile = SAMPLES.resolve(sample.name() + ".actual.ttl");
            write(actual, actualFile);
            String where = "";
            if (Files.exists(accepted)) {
                Model before = ModelFactory.createDefaultModel();
                try (InputStream in = Files.newInputStream(accepted)) {
                    RDFDataMgr.read(before, in, Lang.TURTLE);
                }
                if (digest(before).equals(expected)) {
                    where = "; triples differ for " + differingPredicates(before, actual);
                }
            }
            fail(sample.name() + " converts differently from before" + where + ". The output is in " + actualFile
                + "; if the change is meant, run with -Dgolden.update=true");
        }
        assertConforms(actual, sample.name());
    }

    @ParameterizedTest
    @MethodSource("samples")
    void conformsUnderEveryOption(Sample sample) throws Exception {
        for (boolean keywords : new boolean[] {false, true}) {
            Model m = new Dcm2RdfBuilder().keywords(keywords).oid(true).detlef(true).cdt(true).cdtLevel(2)
                .wkt(true).ptags(true).extra(true).hash(true).toModel(fetch(sample));
            assertConforms(m, sample.name() + (keywords ? " with keywords" : " in hex"));
        }
    }

    @Test
    void rtstructCoversItsFeatures() throws Exception {
        Path file = fetch(RTSTRUCT);
        Model m = new Dcm2RdfBuilder().toModel(file);
        Resource sop = m.createResource("urn:oid:" + RTSTRUCT.uid());
        assertEquals(UID.ImplicitVRLittleEndian, sop.getRequiredProperty(dcm("00020010")).getString());
        // ISO_IR 192: É is two bytes in the file, one character here
        assertEquals("ISO_IR 192", sop.getRequiredProperty(dcm("00080005")).getObject().as(RDFList.class).get(0).asLiteral().getString());
        assertEquals("IRM FÉMUR/CUISSE GAUCHE C- C+ -MUSQ", sop.getRequiredProperty(dcm("00081030")).getString());
        // private tags at the top level, and in a sequence item
        assertEquals("CTP", sop.getRequiredProperty(dcm("00130010")).getResource()
            .getRequiredProperty(DCM.Value).getObject().as(RDFList.class).get(0).asLiteral().getString());
        assertTrue(m.listStatements(null, dcm("37730030"), (RDFNode) null)
            .filterKeep(st -> !st.getSubject().equals(sop)).hasNext(), "a private creator inside an item");
        // four sequences deep: frame of reference / study / series / contour image
        Resource image = firstItem(firstItem(firstItem(firstItem(sop, "30060010"), "30060012"), "30060014"), "30060016");
        assertTrue(image.hasProperty(dcm("00081155")), "the contour image's SOP Instance UID");
        assertEquals(9, m.listStatements(null, dcm("30060050"), (RDFNode) null).toList().size(), "contours");

        Model wkt = new Dcm2RdfBuilder().wkt(true).toModel(file);
        assertEquals(9, wkt.listObjectsOfProperty(dcm("30060050")).filterKeep(o -> o.isLiteral()
            && o.asLiteral().getDatatypeURI().equals(GEO.NS + "wktLiteral")).toList().size(), "contours as WKT");
    }

    @Test
    void bigEndianSampleDecodesItsValues() throws Exception {
        Model m = new Dcm2RdfBuilder().toModel(fetch(PET));
        Resource sop = m.createResource("urn:oid:" + PET.uid());
        assertEquals(UID.ExplicitVRBigEndian, sop.getRequiredProperty(dcm("00020010")).getString());
        // byte order shows in binary values: 192 read the wrong way round is 49152
        assertEquals(192, sop.getRequiredProperty(dcm("00280010")).getInt());
        assertEquals(192, sop.getRequiredProperty(dcm("00280011")).getInt());
        long privates = sop.listProperties().filterKeep(st -> {
            // not getLocalName(): an XML local name can't start with a digit, so Jena finds none
            String local = st.getPredicate().getURI().substring(DCM.NS.length());
            return local.length() == 8 && Character.digit(local.charAt(3), 16) % 2 == 1;
        }).toList().size();
        assertTrue(privates > 150, privates + " private attributes");
        assertFalse(sop.hasProperty(dcm("0009100E")), "the stripped element");
    }

    @Test
    void deflatedConvertsAsTheOriginal(@TempDir Path dir) throws Exception {
        // the RTSTRUCT re-encoded in another transfer syntax, nothing else changed
        Path original = fetch(RTSTRUCT);
        Path deflated = dir.resolve("deflated.dcm");
        try (DicomInputStream in = new DicomInputStream(original.toFile())) {
            Attributes fmi = in.readFileMetaInformation();
            Attributes dataset = in.readDataset();
            fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.DeflatedExplicitVRLittleEndian);
            try (DicomOutputStream out = new DicomOutputStream(Files.newOutputStream(deflated), UID.ExplicitVRLittleEndian)) {
                out.writeDataset(fmi, dataset);
            }
        }
        assertTrue(Files.size(deflated) < Files.size(original) / 2, "deflated");
        Model before = new Dcm2RdfBuilder().toModel(original);
        Model after = new Dcm2RdfBuilder().toModel(deflated);
        Resource sop = after.createResource("urn:oid:" + RTSTRUCT.uid());
        assertEquals(UID.DeflatedExplicitVRLittleEndian, sop.getRequiredProperty(dcm("00020010")).getString());
        before.removeAll(null, dcm("00020010"), null);
        after.removeAll(null, dcm("00020010"), null);
        assertTrue(after.isIsomorphicWith(before),
            "the deflated re-encoding converts differently; triples differ for " + differingPredicates(before, after));
    }

    @Test
    void digestIgnoresBlankNodeLabelsButNotContent() {
        Model a = ModelFactory.createDefaultModel();
        a.createResource("urn:x").addProperty(dcm("00081030"), a.createList(a.createResource().addProperty(DCM.vr, "LO")));
        Model b = ModelFactory.createDefaultModel();
        b.createResource("urn:x").addProperty(dcm("00081030"), b.createList(b.createResource().addProperty(DCM.vr, "LO")));
        assertEquals(digest(a), digest(b));
        b.createResource("urn:x").addProperty(dcm("00080005"), "x");
        assertNotEquals(digest(a), digest(b));
    }
}
