package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.Statistics;
import com.ebremer.dcm2rdf.utils.FileCounter;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.ebremer.dcm2rdf.DirectoryProcessor.FileType;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.jena.rdf.model.Model;

/**
 * The command line's traversal of a source tree.
 * <p>
 * Internal: applications embedding dcm2rdf should use {@link Dcm2RdfBuilder}, whose API is the
 * supported one. This class may change without notice.
 *
 * @author erich
 */
public class DirectoryProcessor {
    public enum FileType {DIRECTORY, DICOMDIR, DICOM, TAR, UNKNOWN};
    private final Parameters params;
    private final FileCounter fc;
    private final Statistics stats = new Statistics();
    private final ProgressBar progressBar;
    // absolute, normalized output paths taken by a source in this run
    private final Set<Path> claimedOutputs = ConcurrentHashMap.newKeySet();
    private static final Logger logger = Logger.getLogger(DirectoryProcessor.class.getName());

    public DirectoryProcessor(Parameters params) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        ProgressBarStyle style;
        if (os.contains("win")) {
            style = ProgressBarStyle.ASCII;
        } else {
            style = ProgressBarStyle.COLORFUL_UNICODE_BLOCK;
        }
        fc = new FileCounter();
        if (params.status) {
            progressBar = new ProgressBarBuilder()
                .setTaskName("Extracting DICOM Metadata...")
                .setInitialMax(0)
                .setStyle(style)
                .build();
        } else {
            progressBar = null;
        }
        this.params = params;
    }

    public FileCounter getFileCounter() {
        return fc;
    }

    public Statistics getStatistics() {
        return stats;
    }

    // The output of a DICOM file or DICOMDIR, or for an archive the path its members' outputs hang
    // from (<archive>#<member>): the source's place in the -src tree, under -dest. A single -src
    // file goes into -dest when that is a folder, and is otherwise written to -dest, which gets
    // the output extension unless it has it already.
    private Path outputFor(Path file, FileType ft) {
        Path src = params.src.toPath();
        Path dest = params.dest.toPath();
        String ext = OutputFiles.extension(params);
        Path base;
        if (Files.isDirectory(src)) {
            base = dest.resolve(src.relativize(file));
        } else if (Files.isDirectory(dest)) {
            base = dest.resolve(file.getFileName());
        } else if (ft != FileType.TAR && dest.toString().toLowerCase(Locale.ROOT).endsWith(ext)) {
            return dest;
        } else {
            base = dest;
        }
        return switch (ft) {
            case DICOM -> Path.of(SourceFiles.stripExtension(base.toString()) + ext);
            case DICOMDIR -> Path.of(base + ext);
            default -> base;
        };
    }

    /** Converts the -src file or tree into -dest. */
    public void process() {
        try (ThreadPoolExecutor engine = new ThreadPoolExecutor(params.threads, params.threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>())) {
            engine.prestartAllCoreThreads();
            Files.walkFileTree(params.src.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    fc.incrementDirectoryCount();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                    FileType ft = SourceFiles.getFileType(p);
                    // -sniff: a file named any other way is DICOM if it carries the DICM marker
                    if (ft == FileType.UNKNOWN && params.sniff && attrs.size() > 0 && SourceFiles.hasDicomPreamble(p)) {
                        ft = FileType.DICOM;
                    }
                    switch (ft) {
                        case DICOM, DICOMDIR -> fc.incrementDicomFileCount();
                        case TAR -> fc.incrementTarFileCount();
                        default -> {
                            fc.incrementOtherFileCount();
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    if (attrs.size() == 0) {
                        logger.log(Level.SEVERE, "Zero Length File {0}", p);
                        stats.addFile(0, 1);
                        fc.incrementZeroLengthFileCount();
                        return FileVisitResult.CONTINUE;
                    }
                    if (params.status) {
                        progressBar.maxHint(fc.getDicomFileCount()+fc.getTarFileCount());
                        progressBar.stepTo(engine.getCompletedTaskCount());
                    }
                    engine.execute(new FileProcessor(params, fc, stats, claimedOutputs, ft, p, outputFor(p, ft)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path p, IOException exc) {
                    // an unreadable file or directory must not abort the rest of the traversal
                    logger.log(Level.SEVERE, String.format("Cannot access %s : %s", p, exc));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (exc != null) {
                        logger.log(Level.SEVERE, String.format("Error reading directory %s : %s", dir, exc));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            engine.shutdown();
            try {
                while (!engine.awaitTermination(1, TimeUnit.SECONDS)) {
                    if (params.status) {
                        progressBar.stepTo(engine.getCompletedTaskCount());
                        progressBar.maxHint(fc.getDicomFileCount()+fc.getTarFileCount());
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.severe(ex.getMessage());
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Traversal of %s failed : %s", params.src, ex), ex);
        }
        if (params.status) {
            System.out.println("\n"+fc);
        }
    }
}

class FileProcessor implements Runnable {
    private final Path file;
    // the file's output; for an archive, the path its members' outputs hang from
    private final Path output;
    private final Parameters params;
    private final FileCounter fc;
    private final Statistics stats;
    private final DirectoryProcessor.FileType ft;
    private final Set<Path> claimedOutputs;
    private static final Logger logger = Logger.getLogger(FileProcessor.class.getName());

    public FileProcessor(Parameters params, FileCounter fc, Statistics stats, Set<Path> claimedOutputs, DirectoryProcessor.FileType ft, Path file, Path output) {
        this.params = params;
        this.fc = fc;
        this.stats = stats;
        this.claimedOutputs = claimedOutputs;
        this.ft = ft;
        this.file = file;
        this.output = output;
    }

    private Model convert(String xfile, InputStream is) {
        DICOM2RDF d2r = new DICOM2RDF(params, stats);
        Model m = d2r.convert(file, xfile, is);
        return d2r.applyPostProcessing(m);
    }

    // A tar entry name is only safe to splice into a destination path if it cannot climb out
    // of it: no absolute form, no Windows drive prefix, no ".." segment (zip-slip). Both
    // separators are checked because Windows resolves '/' and '\'.
    private static boolean hasUnsafePath(String name) {
        if (name.startsWith("/") || name.startsWith("\\")) {
            return true;
        }
        if (name.length() > 1 && name.charAt(1) == ':') {
            return true;
        }
        for (String segment : name.split("[/\\\\]")) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private void processTar(TarArchiveInputStream tarInput, Path root, String srcRoot) throws IOException {
        TarArchiveEntry ce = tarInput.getNextEntry();
        while (ce != null) {
            if (ce.isDirectory()) {
                fc.incrementTarDirectoryCount();
            } else {
                if (ce.getSize()==0) {
                    fc.incrementZeroLengthFileCount();
                    logger.log(Level.SEVERE, "Zero Length File {0}", srcRoot+"#"+ce.getName());
                } else {
                    FileType tft = SourceFiles.getFileType(ce.getName());
                    InputStream entry = tarInput;
                    if (tft == FileType.UNKNOWN && params.sniff) {
                        // look for the DICM marker, then push the bytes read back. (Not a
                        // SequenceInputStream: it closes each stream it finishes, the archive too.)
                        PushbackInputStream pushback = new PushbackInputStream(tarInput, SourceFiles.PREAMBLE_LENGTH);
                        byte[] head = pushback.readNBytes(SourceFiles.PREAMBLE_LENGTH);
                        pushback.unread(head);
                        entry = pushback;
                        if (SourceFiles.isDicomPreamble(head)) {
                            tft = FileType.DICOM;
                        }
                    }
                    if (tft != FileType.UNKNOWN && hasUnsafePath(ce.getName())) {
                        // zip-slip: an entry named "a/../../x.dcm" would place its output outside -dest
                        logger.log(Level.SEVERE, "Rejecting tar entry with unsafe path : {0}", srcRoot+"#"+ce.getName());
                        fc.incrementFailedConversionFileCount();
                    } else {
                        // One bad entry must not abort the rest of the archive: count it and move on.
                        // getNextEntry() skips whatever the failed entry left unread; a broken archive
                        // stream fails there instead and ends the archive.
                        try {
                            switch(tft) {
                                case DICOM, DICOMDIR -> {
                                    fc.incrementTarDicomFileCount();
                                    Path tdest = Path.of(SourceFiles.stripExtension(root.toString()+"#"+ce.getName()) + OutputFiles.extension(params));
                                    processDicom(srcRoot+"#"+ce.getName(), ce.getSize(), tdest, entry);
                                }
                                case TAR -> {
                                    fc.incrementTarTarFileCount();
                                    // The nested archive is the current entry's payload: wrap it in its own tar stream.
                                    // Left unclosed on purpose - closing it would close the outer stream.
                                    processTar(new TarArchiveInputStream(tarInput), Path.of(root.toString(), ce.getName()), srcRoot+"#"+ce.getName());
                                }
                                default -> fc.incrementTarOtherFileCount();
                            }
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, String.format("Conversion failed : %s ---> %s", ex, srcRoot+"#"+ce.getName()), ex);
                            fc.incrementFailedConversionFileCount();
                        }
                    }
                }
            }
            ce = tarInput.getNextEntry();
        }
    }

    // size is the source's size in bytes, for the run statistics
    private void processDicom(String src, long size, Path dest, InputStream is) throws IOException {
        // Distinct sources can map to one output (img.dcm and img.dat both become img.ttl). The first
        // to claim it wins; later ones fail loudly instead of passing as "already done" or racing the write
        if (!claimedOutputs.add(dest.toAbsolutePath().normalize())) {
            logger.log(Level.SEVERE, "Output {0} is already claimed by another source; not converting {1}", new Object[] {dest, src});
            fc.incrementFailedConversionFileCount();
            return;
        }
        if ( !dest.toFile().exists() || params.overwrite ) {
            Model m = convert(src, is);
            stats.addFile(size, 1);
            if (params.cdt) {
                m.setNsPrefix("cdt", "http://w3id.org/awslabs/neptune/SPARQL-CDTs/");
            }
            if ((m!=null)&&(m.size()!=0)) {
                OutputFiles.write(m, dest, params);
            }
            if (!dest.toFile().exists()) {
                logger.log(Level.SEVERE, "Failed to create : {0}", dest);
                fc.incrementFailedConversionFileCount();
                return;
            }
        } else {
            fc.incrementAlreadyConvertedFileCount();
            return;
        }
        fc.incrementConvertedFileCount();
    }

    @Override
    public void run() {
        try {
            switch (ft) {
                // processDicom decides whether an existing output is kept - it must see every source
                // first, to catch two sources claiming the same output
                case DICOM, DICOMDIR -> {
                    if (output.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) {
                        logger.log(Level.SEVERE, "Output would overwrite its source; not converting {0}", file);
                        fc.incrementFailedConversionFileCount();
                        return;
                    }
                    try (FileInputStream fis = new FileInputStream(file.toFile())) {
                        processDicom(file.toString(), Files.size(file), output, fis);
                    }
                }
                case TAR -> {
                    try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new FileInputStream(file.toFile()))) {
                        processTar(tarInput, output, file.toString());
                    }
                }
                default -> {
                    logger.log(Level.SEVERE, "Converting FAIL : {0}", file);
                    fc.incrementFailedConversionFileCount();
                }
            }
        } catch (Throwable t) {
            // nothing waits on the task, so anything thrown here would vanish silently
            logger.log(Level.SEVERE, String.format("Conversion failed : %s ---> %s", t, file), t);
            fc.incrementFailedConversionFileCount();
        }
    }
}
