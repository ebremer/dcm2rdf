package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.RDFFormatter;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
import static com.ebremer.dcm2rdf.RandomUtils.GetFree;
import static com.ebremer.dcm2rdf.RandomUtils.GetMax;
import static com.ebremer.dcm2rdf.RandomUtils.GetTotal;
import com.ebremer.dcm2rdf.utils.Statistics;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */

public class dcm2rdf {    
    public static String Version = "1.1.0";
    private static final Logger logger = Logger.getLogger(dcm2rdf.class.getName());

    public static void main(String[] args) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);                
        Parameters params = new Parameters();
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName("dcm2rdf");    
        try {
            jc.parse(args);
            if (params.help) {
                jc.usage();
                System.out.println("Use -Xmx to set maximum memory.  For example, '-Xmx30G' will set maximum at 30G");
                System.exit(0);
            }
            if (params.src.exists()) {
                if (params.status) {
                    System.out.println("Available # of cores : "+Runtime.getRuntime().availableProcessors());
                    System.out.println("free memory: " + GetFree() + " Total : " + GetTotal() + "  Max: " + GetMax() );
                    System.out.println("Number of cores being used : " + params.threads);
                }
                logger.setLevel(Level.WARNING);
                logger.setUseParentHandlers(false);
                ConsoleHandler consoleHandler = new ConsoleHandler();
                logger.setLevel(params.level);
                logger.addHandler(consoleHandler);      
                FileHandler fileHandler;
                try {
                    fileHandler = new FileHandler(
                    String.format(
                        "dcm2rdf-%s.ttl",
                        Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                        ), false);
                    fileHandler.setLevel(params.level);
                    fileHandler.setFormatter(new RDFFormatter());
                    logger.addHandler(fileHandler);
                    D2R.init();
                    consoleHandler.setLevel(params.level);
                    new DirectoryProcessor(params).Protocol(DICOM);
                    System.out.println(Statistics.getStatistics().getStats());
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                } catch (SecurityException ex) {
                    System.err.println(ex.getMessage());
                }
            } else {
                System.out.println("Source does not exist! "+params.src);
            }
        } catch (ParameterException ex) {
            if (params.version) {
                System.out.println("dcm2rdf - Version : "+Version);
            } else {
                jc.usage();
            }
        }
    }   
}
