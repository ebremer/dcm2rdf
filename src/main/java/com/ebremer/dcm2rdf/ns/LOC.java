package com.ebremer.dcm2rdf.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 *
 * @author erich bremer
 */

public class LOC {

    public static class BibFrame {
        public static final String NS = "http://id.loc.gov/ontologies/bibframe/";
        public static final Property FileSize = ResourceFactory.createProperty(NS+"FileSize");
    }
    
    public static class cryptographicHashFunctions {
        public static final String NS = "http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/";
        public static final Property sha256 = ResourceFactory.createProperty(NS+"sha256");
    }

}
