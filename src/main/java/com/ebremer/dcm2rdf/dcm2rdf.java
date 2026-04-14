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
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */

public class dcm2rdf {
    public static final String VERSION = resolveVersion();

    private static String resolveVersion() {
        String v = dcm2rdf.class.getPackage().getImplementationVersion();
        return v != null ? v : "unknown";
    }

    public static void main(String[] args) {
        Parameters params = new Parameters();
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName("dcm2rdf");
        for (String a : args) {
            if (a.equals("-help") || a.equals("-h")) {
                jc.usage();
                System.out.println("Use -Xmx to set maximum memory.  For example, '-Xmx30G' will set maximum at 30G");
                return;
            }
            if (a.equals("-version")) {
                System.out.println("dcm2rdf - Version : " + VERSION);
                return;
            }
        }
        try {
            jc.parse(args);
            if (params.src.exists()) {
                if (params.status) {
                    System.out.println("Available # of cores : "+Runtime.getRuntime().availableProcessors());
                    System.out.println("free memory: " + GetFree() + " Total : " + GetTotal() + "  Max: " + GetMax() );
                    System.out.println("Number of cores being used : " + params.threads);
                }
                Logger rootLogger = Logger.getLogger("");
                for (Handler h : rootLogger.getHandlers()) {
                    rootLogger.removeHandler(h);
                }
                rootLogger.setLevel(params.level);
                ConsoleHandler consoleHandler = new ConsoleHandler();
                consoleHandler.setLevel(params.level);
                rootLogger.addHandler(consoleHandler);
                try {
                    FileHandler fileHandler = new FileHandler(
                        String.format(
                            "dcm2rdf-%s.ttl",
                            Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                        ), false);
                    fileHandler.setLevel(params.level);
                    fileHandler.setFormatter(new RDFFormatter());
                    rootLogger.addHandler(fileHandler);
                    D2R.init();
                    new DirectoryProcessor(params).Protocol(DICOM);
                    System.out.println(Statistics.getStatistics().getStats());
                } catch (IOException | SecurityException ex) {
                    System.err.println(ex.getMessage());
                }
            } else {
                System.out.println("Source does not exist! "+params.src);
            }
        } catch (ParameterException ex) {
            System.out.println(ex.toString());
            jc.usage();
            System.exit(1);
        }
    }   
}
