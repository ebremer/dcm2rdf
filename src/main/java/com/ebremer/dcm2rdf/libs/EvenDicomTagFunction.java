package com.ebremer.dcm2rdf.libs;

/** SPARQL {@code dcm:isEvenDicomTag(?p)}: whether ?p is a standard (even group) attribute. */
public class EvenDicomTagFunction extends DicomTagParityFunction {

    public EvenDicomTagFunction() {
        super(true);
    }
}
