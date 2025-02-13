package com.ebremer.dcm2rdf.utils;

/**
 *
 * @author Erich Bremer
 */
public class Tools {
    
    public static String padWithZeros(String numericString) {
        if (numericString == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return String.format("%08d", Long.valueOf(numericString));
    }
}
