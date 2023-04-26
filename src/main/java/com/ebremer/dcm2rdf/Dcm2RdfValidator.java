package com.ebremer.dcm2rdf;

import com.beust.jcommander.IParameterValidator;

public class Dcm2RdfValidator implements IParameterValidator {

    public Dcm2RdfValidator() {}
    
    @Override
    public void validate(String parameterName, String parameterValue) {
        System.out.println("Validator : "+parameterName+" --> "+parameterValue);
      // No validation is performed
    }
}
