package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.IStringConverter;

public class BooleanConverter implements IStringConverter<Boolean> {

    @Override
    public Boolean convert(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        String lowerCaseValue = value.toLowerCase();
        if (null == lowerCaseValue) {
            throw new IllegalArgumentException("Invalid boolean value: " + value);
        } else switch (lowerCaseValue) {
            case "true", "t", "1" -> {
                return true;
            }
            case "false", "f", "0" -> {
                return false;
            }
            default -> throw new IllegalArgumentException("Invalid boolean value: " + value);
        }
    }
}
