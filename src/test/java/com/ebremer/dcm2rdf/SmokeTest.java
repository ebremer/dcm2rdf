package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.parameters.Parameters;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Converts the {@link SmokeFixtures} set under each option combination the native-image CI
 * job also runs, checks every output is produced and parses, and validates it against the
 * bundled SHACL shapes (all but the long form, which the shapes don't cover).
 */
class SmokeTest {

    private static final Shapes SHAPES = Shapes.parse(RDFDataMgr.loadGraph(
        SmokeTest.class.getResource("/shacl.ttl").toString(), Lang.TURTLE));

    private record OptionSet(Consumer<Parameters> settings, int outputs, boolean shaped) {
    }

    static Stream<Arguments> optionSets() {
        return Stream.of(
            options("defaults", p -> { }),
            options("every tweak", p -> {
                p.extra = true;
                p.hash = true;
                p.oid = true;
                p.wkt = true;
                p.detlef = true;
                p.cdt = true;
                p.cdtlevel = 2;
                p.ptags = true;
                p.padleftzero = true;
                p.includeinlinebinary = true;
                p.threads = 4;
            }),
            options("keywords, SHA256 naming, gzipped N-Triples", p -> {
                p.keywords = true;
                p.naming = "SHA256";
                p.format = Parameters.RdfFormat.NT;
                p.compress = true;
                p.oid = true;
                p.wkt = true;
                p.detlef = true;
                p.cdt = true;
                p.ptags = true;
            }),
            Arguments.of(Named.of("sniffing", new OptionSet(p -> p.sniff = true, SmokeFixtures.OUTPUTS_SNIFFED, true))),
            Arguments.of(Named.of("long form", new OptionSet(p -> p.longForm = true, SmokeFixtures.OUTPUTS, false))));
    }

    private static Arguments options(String name, Consumer<Parameters> settings) {
        return Arguments.of(Named.of(name, new OptionSet(settings, SmokeFixtures.OUTPUTS, true)));
    }

    @ParameterizedTest
    @MethodSource("optionSets")
    void convertsTheWholeSet(OptionSet set, @TempDir Path dest) throws Exception {
        Parameters params = new Parameters();
        params.src = Path.of(SmokeTest.class.getResource("/smoke").toURI()).toFile();
        params.dest = dest.toFile();
        set.settings().accept(params);
        DirectoryProcessor dp = new DirectoryProcessor(params);
        dp.process();
        assertEquals(0, dp.getFileCounter().getFailedConversionFileCount());
        assertEquals(set.outputs(), dp.getFileCounter().getConvertedFileCount());
        List<Path> outputs;
        try (var walk = Files.walk(dest)) {
            outputs = walk.filter(Files::isRegularFile).toList();
        }
        assertEquals(set.outputs(), outputs.size(), outputs.toString());
        for (Path out : outputs) {
            Model m = load(out);
            assertFalse(m.isEmpty(), "empty output " + out);
            if (set.shaped()) {
                ValidationReport report = ShaclValidator.get().validate(SHAPES, m.getGraph());
                if (!report.conforms()) {
                    ShLib.printReport(report);
                }
                assertTrue(report.conforms(), "does not conform to shacl.ttl: " + out);
            }
        }
    }

    // tar entry outputs have a '#' in their name, so Jena can't go by the file name
    private static Model load(Path out) throws Exception {
        String name = out.getFileName().toString();
        boolean gz = name.endsWith(".gz");
        Lang lang = name.endsWith(".nt") || name.endsWith(".nt.gz") ? Lang.NTRIPLES : Lang.TURTLE;
        Model m = ModelFactory.createDefaultModel();
        try (InputStream in = gz ? new GZIPInputStream(Files.newInputStream(out)) : Files.newInputStream(out)) {
            RDFDataMgr.read(m, in, lang);
        }
        return m;
    }
}
