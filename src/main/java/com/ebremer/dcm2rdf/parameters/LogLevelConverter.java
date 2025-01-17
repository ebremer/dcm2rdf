package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.IStringConverter;
import java.util.logging.Level;

public class LogLevelConverter implements IStringConverter<Level> {

    @Override
    public Level convert(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Level must be either OFF, ALL, WARNING, SEVERE");
        }
        return switch (value.toUpperCase()) {
            case "ALL" -> Level.ALL;
            case "WARNING" -> Level.WARNING;
            case "SEVERE" -> Level.SEVERE;
            default -> Level.SEVERE;
        };
    }
}
