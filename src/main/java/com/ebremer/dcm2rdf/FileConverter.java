package com.ebremer.dcm2rdf;

import com.beust.jcommander.IStringConverter;
import java.io.File;

public class FileConverter implements IStringConverter<File> {

    @Override
    public File convert(String value) {
        System.out.println("YAY!!! "+value);
        return new File(value);
    }
}
