package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class PositiveInteger implements IParameterValidator {

    @Override
    public void validate(String name, String value) throws ParameterException {
        int n;
        try {
            n = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ParameterException("Parameter " + name + " should be a positive integer (found " + value + ")");
        }
        if (n < 1) {
            throw new ParameterException("Parameter " + name + " should be positive (found " + value + ")");
        }
    }
}
