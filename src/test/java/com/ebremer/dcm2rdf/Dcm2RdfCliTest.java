package com.ebremer.dcm2rdf;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The command line as a whole, through {@link Dcm2RdfCli#run}: its exit codes, where its messages
 * go, and where a single file's output goes.
 */
class Dcm2RdfCliTest {

    private static final Path SMOKE = Path.of("src/test/resources/smoke");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream stdout;
    private PrintStream stderr;

    @BeforeEach
    void captureConsole() {
        stdout = System.out;
        stderr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreConsole() {
        System.setOut(stdout);
        System.setErr(stderr);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private static int run(Path logdir, String... args) {
        String[] all = Stream.concat(Stream.of(args), Stream.of("-logdir", logdir.toString())).toArray(String[]::new);
        return Dcm2RdfCli.run(all);
    }

    private static long outputs(Path dir) throws Exception {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void helpAndVersionExitZero() {
        assertEquals(Dcm2RdfCli.OK, Dcm2RdfCli.run(new String[] {"-help"}));
        assertEquals(Dcm2RdfCli.OK, Dcm2RdfCli.run(new String[] {"-version"}));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains(Dcm2RdfCli.VERSION));
    }

    @Test
    void badOptionsExitOneWithTheReasonOnStderr(@TempDir Path dir) {
        assertEquals(Dcm2RdfCli.USAGE, run(dir, "-src", SMOKE.toString(), "-dest", dir.toString(), "-bogus"));
        assertTrue(err().contains("-bogus"), err());
        assertTrue(err().contains("Usage: dcm2rdf"), "the usage follows the reason");
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Usage"), "nothing on stdout");
    }

    @Test
    void flagsTakeNoValue(@TempDir Path dir) {
        // -c is on by being there; "false" is left over, not read as its value
        assertEquals(Dcm2RdfCli.USAGE, run(dir, "-src", SMOKE.toString(), "-dest", dir.toString(), "-c", "false"));
        assertTrue(err().contains("false"), err());
    }

    @Test
    void cdtLevelMustBePositive(@TempDir Path dir) {
        for (String level : List.of("0", "-1")) {
            assertEquals(Dcm2RdfCli.USAGE, run(dir, "-src", SMOKE.toString(), "-dest", dir.toString(), "-cdt", "-cdtlevel", level));
            assertTrue(err().contains("-cdtlevel"), err());
        }
        assertThrows(IllegalArgumentException.class, () -> new Dcm2RdfBuilder().cdtLevel(0));
    }

    @Test
    void missingSourceExitsOne(@TempDir Path dir) {
        assertEquals(Dcm2RdfCli.USAGE, run(dir, "-src", dir.resolve("nope").toString(), "-dest", dir.resolve("out").toString()));
        assertTrue(err().contains("Source does not exist"), err());
    }

    @Test
    void aCleanRunExitsZeroAndOneWithFailuresExitsTwo(@TempDir Path dir) throws Exception {
        Path dest = dir.resolve("out");
        assertEquals(Dcm2RdfCli.OK, run(dir, "-src", SMOKE.toString(), "-dest", dest.toString()));
        assertEquals(SmokeFixtures.OUTPUTS, outputs(dest));

        Path src = Files.createDirectories(dir.resolve("src"));
        byte[] good = Files.readAllBytes(SMOKE.resolve("ct.dcm"));
        Files.write(src.resolve("good.dcm"), good);
        Files.write(src.resolve("cut.dcm"), java.util.Arrays.copyOf(good, good.length / 2));
        assertEquals(Dcm2RdfCli.FAILURES, run(dir, "-src", src.toString(), "-dest", dir.resolve("out2").toString()));
    }

    @Test
    void theRunLogParsesAsTurtle(@TempDir Path dir) throws Exception {
        // a source name with a space, an apostrophe and non-ASCII letters, a value with quotes in
        // its message, and a failure with a stack trace: all must survive into valid Turtle
        Path src = Files.createDirectories(dir.resolve("src d'été"));
        byte[] good = Files.readAllBytes(SMOKE.resolve("ct.dcm"));
        Files.write(src.resolve("cut ü.dcm"), java.util.Arrays.copyOf(good, good.length / 2));
        Files.write(src.resolve("ct.dcm"), good);
        Path logdir = dir.resolve("logs");
        assertEquals(Dcm2RdfCli.FAILURES, run(logdir, "-src", src.toString(), "-dest", dir.resolve("out").toString(), "-level", "FINE"));
        List<Path> logs;
        try (Stream<Path> files = Files.list(logdir)) {
            logs = files.filter(p -> p.getFileName().toString().matches("dcm2rdf-.*\\.log\\.ttl")).toList();
        }
        assertEquals(1, logs.size(), logs.toString());
        org.apache.jena.rdf.model.Model log = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        try (var in = Files.newInputStream(logs.get(0))) {
            org.apache.jena.riot.RDFDataMgr.read(log, in, org.apache.jena.riot.Lang.TURTLE);
        }
        String ns = com.ebremer.dcm2rdf.utils.RDFFormatter.NS;
        org.apache.jena.rdf.model.Property message = log.createProperty(ns, "message");
        org.apache.jena.rdf.model.Property levelOf = log.createProperty(ns, "level");
        assertTrue(log.contains(null, levelOf, log.createResource(ns + "SEVERE")), "the failure");
        assertTrue(log.listObjectsOfProperty(message).toList().stream()
            .anyMatch(o -> o.asLiteral().getString().contains("cut ü.dcm")), "names keep their letters");
        assertTrue(log.listObjectsOfProperty(message).toList().stream()
            .anyMatch(o -> o.asLiteral().getString().contains("Invalid ") && o.asLiteral().getString().contains("\"")), "values keep their quotes");
    }

    @Test
    void theCallersLoggingIsRestored(@TempDir Path dir) {
        Logger root = Logger.getLogger("");
        List<Handler> before = List.of(root.getHandlers());
        Level level = root.getLevel();
        run(dir, "-src", SMOKE.toString(), "-dest", dir.resolve("out").toString(), "-level", "OFF");
        assertEquals(before, List.of(root.getHandlers()));
        assertEquals(level, root.getLevel());
    }

    @Test
    void aSingleFileGoesToDestOrIntoIt(@TempDir Path dir) throws Exception {
        Path ct = SMOKE.resolve("ct.dcm");
        // -dest names the output, with or without its extension
        assertEquals(Dcm2RdfCli.OK, run(dir, "-src", ct.toString(), "-dest", dir.resolve("a.ttl").toString()));
        assertTrue(Files.exists(dir.resolve("a.ttl")));
        assertFalse(Files.exists(dir.resolve("a.ttl.ttl")));
        assertEquals(Dcm2RdfCli.OK, run(dir, "-src", ct.toString(), "-dest", dir.resolve("b").toString()));
        assertTrue(Files.exists(dir.resolve("b.ttl")));
        assertEquals(Dcm2RdfCli.OK, run(dir, "-src", ct.toString(), "-dest", dir.resolve("c.nt.gz").toString(), "-format", "NT", "-c"));
        assertTrue(Files.exists(dir.resolve("c.nt.gz")));
        // an existing folder takes it, named after the source
        Path folder = Files.createDirectories(dir.resolve("folder"));
        assertEquals(Dcm2RdfCli.OK, run(dir, "-src", ct.toString(), "-dest", folder.toString()));
        assertTrue(Files.exists(folder.resolve("ct.ttl")));
    }

    @Test
    void anOutputNeverReplacesItsSource(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("img.ttl");
        byte[] dicom = Files.readAllBytes(SMOKE.resolve("ct.dcm"));
        Files.write(src, dicom);
        assertEquals(Dcm2RdfCli.FAILURES, run(dir, "-src", src.toString(), "-dest", src.toString(), "-sniff", "-overwrite"));
        assertArrayEquals(dicom, Files.readAllBytes(src));
    }
}
