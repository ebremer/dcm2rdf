package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.libs.EvenDicomTagFunction;
import com.ebremer.dcm2rdf.libs.OddDicomTagFunction;
import com.ebremer.dcm2rdf.libs.RdfListToCdtFunction;
import com.ebremer.dcm2rdf.ns.DCM;
import org.apache.jena.sparql.function.FunctionRegistry;


/**
 * Registers the SPARQL functions the post-processing queries call.
 *
 * @author erich
 */
public final class D2R {
    private static D2R d2r = null;

    private D2R() {
        FunctionRegistry.get().put(DCM.NS+"isEvenDicomTag", EvenDicomTagFunction.class);
        FunctionRegistry.get().put(DCM.NS+"isOddDicomTag", OddDicomTagFunction.class);
        FunctionRegistry.get().put(DCM.NS+"rdf2cdtList", RdfListToCdtFunction.class);
    }
    
    public synchronized static void init() {
        if (d2r == null) {
            d2r = new D2R();
        }
    }
}
