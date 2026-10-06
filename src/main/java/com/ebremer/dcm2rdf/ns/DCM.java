package com.ebremer.dcm2rdf.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.dcm4che3.data.VR;

/**
 *
 * @author erich bremer
 */
public class DCM {    
    // not the NEMA namespace, http://dicom.nema.org/resources/ontology/DCM/ (declined)
    public static final String NS = "https://halcyon.is/dicom/ns/"; // Replace with community version
    
    public static final Resource Null = ResourceFactory.createResource(NS+"Null");
    public static final Resource SOPInstance = ResourceFactory.createResource(NS+"SOPInstance");    
    public static final Property invalidSOPInstance = ResourceFactory.createProperty(NS+"invalidSOPInstance");
    public static final Property InlineBinary = ResourceFactory.createProperty(NS,"InlineBinary");
    /** Byte length of an inline binary value left out of the output (no -includeinlinebinary). */
    public static final Property InlineBinaryOmitted = ResourceFactory.createProperty(NS,"InlineBinaryOmitted");
    /** Byte length of a bulk data value (overlay plane, waveform, document, ...) left out of the output. */
    public static final Property BulkDataOmitted = ResourceFactory.createProperty(NS,"BulkDataOmitted");
    public static final Property Value = ResourceFactory.createProperty(NS,"Value");
    public static final Property vr = ResourceFactory.createProperty(NS,"vr");
    public static final Property BulkDataURI = ResourceFactory.createProperty(NS,"BulkDataURI");

    /** Datatype of a value that does not parse for its VR, e.g. dcm:invalidDA; the lexical form is the raw value. */
    public static String invalid(VR vr) {
        return NS + "invalid" + vr.name();
    }
}
