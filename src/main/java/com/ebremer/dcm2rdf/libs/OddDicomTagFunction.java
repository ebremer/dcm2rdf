package com.ebremer.dcm2rdf.libs;

/** SPARQL {@code dcm:isOddDicomTag(?p)}: whether ?p is a private (odd group) attribute. */
public class OddDicomTagFunction extends DicomTagParityFunction {

    public OddDicomTagFunction() {
        super(false);
    }
}
