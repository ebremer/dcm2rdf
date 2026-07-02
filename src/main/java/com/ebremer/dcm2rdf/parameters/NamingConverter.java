package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

public class NamingConverter implements IStringConverter<String> {

    @Override
    public String convert(String value) {
        if ("SOPInstanceUID".equalsIgnoreCase(value)) {
            return "SOPInstanceUID";
        }
        if ("SHA256".equalsIgnoreCase(value)) {
            return "SHA256";
        }
        throw new ParameterException("Invalid -naming value '" + value + "'. Must be SOPInstanceUID or SHA256");
    }
}
