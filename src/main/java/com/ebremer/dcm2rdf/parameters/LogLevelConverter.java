package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import java.util.logging.Level;

public class LogLevelConverter implements IStringConverter<Level> {
    private static final String ALLOWED = "OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL";

    @Override
    public Level convert(String value) {
        if (value == null || value.isEmpty()) {
            throw new ParameterException("-level requires a value: " + ALLOWED);
        }
        try {
            return Level.parse(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ParameterException("Invalid -level value '" + value + "'. Must be one of " + ALLOWED);
        }
    }
}
