package com.ebremer.dcm2rdf.utils;

/**
 *
 * @author erich
 */
public class HashGenerationException extends Exception {

    public HashGenerationException() {
        super();
    }
	
    public HashGenerationException(String message, Throwable throwable) {
	super(message, throwable);
    }

    public HashGenerationException(String message) {
	super(message);
    }

    public HashGenerationException(Throwable throwable) {
	super(throwable);
    }
}