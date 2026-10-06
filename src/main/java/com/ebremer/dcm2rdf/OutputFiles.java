package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.GEO;
import com.ebremer.dcm2rdf.ns.PROVO;
import com.ebremer.dcm2rdf.parameters.Parameters;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 * Naming and writing the command line's output files.
 */
final class OutputFiles {

    private OutputFiles() {}

    /** The output extension the run's options give: ".ttl" or ".nt", with -c ".ttl.gz" or ".nt.gz". */
    static String extension(Parameters params) {
        return "." + params.format.getExtension() + (params.compress ? ".gz" : "");
    }

    static void write(Model m, Path file, Parameters params) throws IOException {
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("prov", PROVO.NS);
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("geo", GEO.NS);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Write to a temp file and move it into place, so a failed run can't leave a
        // partial output that later runs mistake for a finished conversion
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            try (OutputStream fos = params.compress
                    ? new GZIPOutputStream(new FileOutputStream(tmp.toFile()))
                    : new FileOutputStream(tmp.toFile())) {
                RDFDataMgr.write(fos, m, params.format.getRDFFormat());
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException suppressed) {
                ex.addSuppressed(suppressed);
            }
            throw ex;
        }
    }
}
