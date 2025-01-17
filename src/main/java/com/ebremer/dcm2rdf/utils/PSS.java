package com.ebremer.dcm2rdf.utils;

import com.ebremer.dcm2rdf.ns.DCM;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author erich
 */
public class PSS {
    
    public static String get(String cmd) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(cmd);
        pss.setNsPrefix("dcm", DCM.NS);
        pss.setNsPrefix("rdf", RDF.getURI());
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("xsd", XSD.NS);
        return pss.toString();
    }
    
    public static ParameterizedSparqlString getPSS(String cmd) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(cmd);
        pss.setNsPrefix("dcm", DCM.NS);
        pss.setNsPrefix("rdf", RDF.getURI());
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("xsd", XSD.NS);
        return pss;
    }
}
