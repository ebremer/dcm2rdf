package com.ebremer.dcm2rdf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
//import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */

public class dcm2rdf {
    public static String Version = "1.0.0";

    public static void main(String[] args) {
//        loci.common.DebugTools.setRootLevel("WARN");
        //ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        //root.setLevel(ch.qos.logback.classic.Level.OFF);
        Parameters params = new Parameters();
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName("dcm2rdf");    
        try {
            jc.parse(args);
            if (params.help) {
                jc.usage();
            } if (params.version) {
                System.out.println("dcm2rdf - Version : "+Version);
            } else {
                new DirectoryProcessor(params).Protocol(DICOM);
            }
        } catch (ParameterException ex) {
            System.out.println(ex.getMessage());
        }
    }   
}
