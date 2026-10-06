package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.parameters.Parameters;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class OutputFilesTest {

    @Test
    void writeCreatesParentDirsAndLeavesNoTempFile(@TempDir Path dir) throws Exception {
        Model m = ModelFactory.createDefaultModel();
        m.add(m.createResource("urn:test:s"), m.createProperty("urn:test:p"), "o");
        Path out = dir.resolve("sub").resolve("out.ttl");
        OutputFiles.write(m, out, new Parameters());
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
        assertFalse(Files.exists(out.resolveSibling("out.ttl.tmp")));
    }

    @Test
    void extensionFollowsFormatAndCompression() {
        Parameters params = new Parameters();
        assertEquals(".ttl", OutputFiles.extension(params));
        params.compress = true;
        assertEquals(".ttl.gz", OutputFiles.extension(params));
        params.format = Parameters.RdfFormat.NT;
        assertEquals(".nt.gz", OutputFiles.extension(params));
    }
}
