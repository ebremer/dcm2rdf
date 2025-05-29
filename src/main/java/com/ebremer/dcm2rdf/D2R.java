package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.libs.isDicomTag;
import com.ebremer.dcm2rdf.libs.isEvenDicomTag;
import com.ebremer.dcm2rdf.libs.isOddDicomTag;
import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.utils.ListPosition;
import com.ebremer.dcm2rdf.utils.rdf2cdtList;
import org.apache.jena.sparql.function.FunctionRegistry;


/**
 *
 * @author erich
 */
public final class D2R {
    private static D2R d2r = null;

    private D2R() {
        FunctionRegistry.get().put(DCM.NS+"isEvenDicomTag", isEvenDicomTag.class);                                
        FunctionRegistry.get().put(DCM.NS+"isOddDicomTag", isOddDicomTag.class);
        FunctionRegistry.get().put(DCM.NS+"ListPosition", ListPosition.class);
        FunctionRegistry.get().put(DCM.NS+"rdf2cdtList", rdf2cdtList.class);
        FunctionRegistry.get().put(DCM.NS+"isDicomTag", isDicomTag.class);
    }
    
    public synchronized static void init() {
        if (d2r == null) {
            d2r = new D2R();
        }
    }
}
