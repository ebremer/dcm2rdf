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
        // pad as text: parsing as a number fails on IDs that aren't one, or are too long for a long
        int pad = 8 - numericString.length();
        return pad > 0 ? "0".repeat(pad) + numericString : numericString;
    }
}
