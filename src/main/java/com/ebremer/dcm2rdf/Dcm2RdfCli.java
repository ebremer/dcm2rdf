package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.LazyFileHandler;
import com.ebremer.dcm2rdf.utils.RDFFormatter;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dcm2rdf command line.
 * <p>
 * Exit codes: {@value #OK} when every source was converted (or already had its output),
 * {@value #USAGE} when the run could not start, and {@value #FAILURES} when it finished but some
 * sources failed to convert.
 *
 * @author erich
 */
public final class Dcm2RdfCli {
    public static final String VERSION = resolveVersion();
    /** Every source was converted, or already had its output. */
    public static final int OK = 0;
    /** The run could not start: bad options, a missing source, or an unusable log directory. */
    public static final int USAGE = 1;
    /** The run finished, but some sources failed to convert; the log names them. */
    public static final int FAILURES = 2;

    private Dcm2RdfCli() {}

    // from a resource Maven fills in, so builds without a jar manifest (the native image, the
    // classes directory) know it too
    private static String resolveVersion() {
        try (InputStream in = Dcm2RdfCli.class.getResourceAsStream("version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("version");
                if (v != null && !v.startsWith("${")) {
                    return v;
                }
            }
        } catch (IOException ex) {
            // fall back to the manifest
        }
        String v = Dcm2RdfCli.class.getPackage().getImplementationVersion();
        return v != null ? v : "unknown";
    }

    private static long mb(long bytes) {
        return bytes / (1024L * 1024L);
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Runs the command line and returns its exit code, without exiting the JVM. */
    public static int run(String[] args) {
        Parameters params = new Parameters();
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName("dcm2rdf");
        for (String a : args) {
            if (a.equals("-help") || a.equals("-h")) {
                jc.usage();
                System.out.println("Use -Xmx to set maximum memory.  For example, '-Xmx30G' will set maximum at 30G");
                return OK;
            }
            if (a.equals("-version")) {
                System.out.println("dcm2rdf - Version : " + VERSION);
                return OK;
            }
        }
        try {
            jc.parse(args);
        } catch (ParameterException ex) {
            System.err.println(ex.getMessage());
            StringBuilder usage = new StringBuilder();
            jc.getUsageFormatter().usage(usage);
            System.err.print(usage);
            return USAGE;
        }
        if (!params.src.exists()) {
            System.err.println("Source does not exist! " + params.src);
            return USAGE;
        }
        if (params.status) {
            Runtime rt = Runtime.getRuntime();
            System.out.println("Available # of cores : " + rt.availableProcessors());
            System.out.println("Free memory : " + mb(rt.freeMemory()) + " MB  Total : " + mb(rt.totalMemory()) + " MB  Max : " + mb(rt.maxMemory()) + " MB");
            System.out.println("Number of cores being used : " + params.threads);
        }
        // the run owns the root logger until it ends; the caller's handlers come back afterwards
        Logger rootLogger = Logger.getLogger("");
        Handler[] callerHandlers = rootLogger.getHandlers();
        Level callerLevel = rootLogger.getLevel();
        for (Handler h : callerHandlers) {
            rootLogger.removeHandler(h);
        }
        try {
            rootLogger.setLevel(params.level);
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(params.level);
            rootLogger.addHandler(consoleHandler);
            Path logdir = params.logdir.toPath();
            Files.createDirectories(logdir);
            LazyFileHandler fileHandler = new LazyFileHandler(logdir.resolve(String.format(
                "dcm2rdf-%s.log.ttl",
                Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")))));
            fileHandler.setLevel(params.level);
            fileHandler.setFormatter(new RDFFormatter());
            // the log is Turtle, which is UTF-8 whatever the platform's default
            fileHandler.setEncoding(StandardCharsets.UTF_8.name());
            rootLogger.addHandler(fileHandler);
            D2R.init();
            DirectoryProcessor dp = new DirectoryProcessor(params);
            dp.process();
            System.out.println(dp.getStatistics().getStats());
            return dp.getFileCounter().getFailedConversionFileCount() > 0 ? FAILURES : OK;
        } catch (IOException | SecurityException ex) {
            System.err.println(ex.getMessage());
            return USAGE;
        } finally {
            for (Handler h : rootLogger.getHandlers()) {
                rootLogger.removeHandler(h);
                h.close();
            }
            for (Handler h : callerHandlers) {
                rootLogger.addHandler(h);
            }
            rootLogger.setLevel(callerLevel);
        }
    }
}
