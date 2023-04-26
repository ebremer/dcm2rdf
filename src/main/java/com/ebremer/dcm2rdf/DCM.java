package com.ebremer.dcm2rdf;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 *
 * @author erich
 */
public class DCM {
    
    //public static final String NS = "http://dicom.nema.org/resources/ontology/DCM/";  // David said no
    public static final String NS = "https://halcyon.is/dicom/ns/";
    
    public static final Resource Null = ResourceFactory.createResource(NS+"Null");
    public static final Resource SOPInstance = ResourceFactory.createResource(NS+"SOPInstance");
    
    public static final Property invalidSOPInstance = ResourceFactory.createProperty(NS+"invalidSOPInstance");
    public static final Property InlineBinary = ResourceFactory.createProperty(NS,"InlineBinary");
    public static final Property Value = ResourceFactory.createProperty(NS,"Value");
    public static final Property vr = ResourceFactory.createProperty(NS,"vr");
    public static final Property BulkDataURI = ResourceFactory.createProperty(NS,"BulkDataURI");
    public static final Property _00081190 = ResourceFactory.createProperty(NS,"00081190");
    public static final Property _30060050 = ResourceFactory.createProperty(NS,"30060050");
    public static final Property _30060042 = ResourceFactory.createProperty(NS,"30060042");
}
