package com.ebremer.dcm2rdf.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.vocabulary.SHACLM;

/**
 * Loads the bundled DICOM SHACL shapes once and shares them. The model is read-only after
 * construction, so concurrent readers (one conversion task per thread) are safe.
 *
 * @author erich
 */
public class SHACL {
    private final Model m;

    private static class Holder {
        private static final SHACL INSTANCE = new SHACL();
    }

    private SHACL() {
        m = ModelFactory.createDefaultModel();
        m.setNsPrefix("sh", SHACLM.NS);
        try (InputStream fis = SHACL.class.getResourceAsStream("/shacl.ttl")) {
            if (fis == null) {
                throw new IllegalStateException("shacl.ttl not found on classpath");
            }
            RDFDataMgr.read(m, fis, Lang.TURTLE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read shacl.ttl", ex);
        }
    }

    public Model getModel() {
        return m;
    }

    public static SHACL getInstance() {
        return Holder.INSTANCE;
    }
}
