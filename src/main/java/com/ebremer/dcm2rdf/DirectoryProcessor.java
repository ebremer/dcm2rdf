package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.Statistics;
import com.ebremer.dcm2rdf.utils.FileCounter;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.ebremer.dcm2rdf.DirectoryProcessor.FileType;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOMDIR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.TAR;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.Callable;
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
 *
 * @author erich
 */
public class DirectoryProcessor {
    public enum FileType {DIRECTORY, DICOMDIR, DICOM, TAR, UNKNOWN};
    private final Parameters params;
    private final FileCounter fc;
    private final ProgressBar progressBar;
    // absolute, normalized output paths taken by a source in this run
    private final Set<Path> claimedOutputs = ConcurrentHashMap.newKeySet();
    private static final Logger logger = Logger.getLogger(dcm2rdf.class.getName());

    public DirectoryProcessor(Parameters params) {
        String os = System.getProperty("os.name").toLowerCase();
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

    public void Traverse(Parameters params) {
        try (ThreadPoolExecutor engine = new ThreadPoolExecutor(params.threads, params.threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>())) {
            engine.prestartAllCoreThreads();
            Files.walkFileTree(params.src.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (params.status) fc.incrementDirectoryCount();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                    FileType ft = RandomUtils.getFileType(p);
                    switch (ft) {
                        case DICOM, DICOMDIR -> {
                            if (params.status) fc.incrementDicomFileCount();
                        }
                        case TAR -> {
                            if (params.status) fc.incrementTarFileCount();
                        }
                        default -> {
                            if (params.status) fc.incrementOtherFileCount();
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    if (attrs.size() == 0) {
                        logger.log(Level.SEVERE, "Zero Length File {0}", p);
                        Statistics.getStatistics().AddFile(0, 1);
                        if (params.status) fc.incrementZeroLengthFileCount();
                        return FileVisitResult.CONTINUE;
                    }
                    if (params.status) {
                        progressBar.maxHint(fc.getDicomFileCount()+fc.getTarFileCount());
                        progressBar.stepTo(engine.getCompletedTaskCount());
                    }
                    engine.submit(new FileProcessor(params,fc,claimedOutputs,ft,p));
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
    
    public void Protocol(FileType ftype) {
        Traverse(params);
    }
}

class FileProcessor implements Callable<Model> {
    private final Path file;
    private final Parameters params;
    private final FileCounter fc;
    private final DirectoryProcessor.FileType ft;
    private final Set<Path> claimedOutputs;
    private static final Logger logger = Logger.getLogger(dcm2rdf.class.getName());
    private enum STAT { CREATED, FAILED, ALREADYDONE };

    public FileProcessor(Parameters params, FileCounter fc, Set<Path> claimedOutputs, DirectoryProcessor.FileType ft, Path file) {
        this.params = params;
        this.fc = fc;
        this.claimedOutputs = claimedOutputs;
        this.ft = ft;
        this.file = file;
    }

    public Model ScanMeta(Parameters params, String xfile, InputStream is) {
        DICOM2RDF d2r = new DICOM2RDF(params);
        Model m = d2r.ProcessDICOMasBytes2Model(file, xfile, is);
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

    private void ProcessTar(TarArchiveInputStream tarInput, Path root, String srcRoot) throws IOException {
        TarArchiveEntry ce = tarInput.getNextEntry();
        while (ce != null) {
            if (ce.isDirectory()) {
                if (params.status) fc.incrementTarDirectoryCount();
            } else {
                if (ce.getSize()==0) {
                    if (params.status) fc.incrementZeroLengthFileCount();
                    logger.log(Level.SEVERE, "Zero Length File {0}", srcRoot+"#"+ce.getName());
                } else {
                    FileType tft = RandomUtils.getFileType(ce.getName());
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
                                    if (params.status) fc.incrementTarDicomFileCount();
                                    String tdest = RandomUtils.StripExtension(root.toString()+"#"+ce.getName())
                                        +(params.compress?String.format(".%s.gz",params.format.getExtension()):"."+params.format.getExtension());
                                    ProcessDICOM(params, srcRoot+"#"+ce.getName(), tdest, tarInput);
                                }
                                case TAR -> {
                                    if (params.status) fc.incrementTarTarFileCount();
                                    // The nested archive is the current entry's payload: wrap it in its own tar stream.
                                    // Left unclosed on purpose - closing it would close the outer stream.
                                    ProcessTar(new TarArchiveInputStream(tarInput), Path.of(root.toString(), ce.getName()), srcRoot+"#"+ce.getName());
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
    
    private STAT ProcessDICOM(Parameters params, String src, String fdest, InputStream is) throws IOException {
        Path dest = Paths.get(fdest);
        // Distinct sources can map to one output (img.dcm and img.dat both become img.ttl). The first
        // to claim it wins; later ones fail loudly instead of passing as "already done" or racing the write
        if (!claimedOutputs.add(dest.toAbsolutePath().normalize())) {
            logger.log(Level.SEVERE, "Output {0} is already claimed by another source; not converting {1}", new Object[] {dest, src});
            fc.incrementFailedConversionFileCount();
            return STAT.FAILED;
        }
        if ( !dest.toFile().exists() || params.overwrite ) {
            Model m = ScanMeta(params, src, is);
            if (params.cdt) {
                m.setNsPrefix("cdt", "http://w3id.org/awslabs/neptune/SPARQL-CDTs/");
            }
            if ((m!=null)&&(m.size()!=0)) {
                RandomUtils.DumpModel(m,dest,params);
            }
            if (!dest.toFile().exists()) {
                logger.log(Level.SEVERE, "Failed to create : {0}", dest);
                fc.incrementFailedConversionFileCount();
                return STAT.FAILED;
            }
        } else {
            return STAT.ALREADYDONE;
        }
        return STAT.CREATED;
    }

    @Override
    public Model call() {
        try {
            String frag = Path.of(params.dest.toString(), params.src.toPath().relativize(file).toString()).toString();
            switch (ft) {
                // ProcessDICOM decides whether an existing output is kept - it must see every source
                // first, to catch two sources claiming the same output
                case DICOM -> {
                    try (FileInputStream fis = new FileInputStream(file.toFile())) {
                        Path xdest = Paths.get(RandomUtils.StripExtension(frag)+(params.compress?String.format(".%s.gz",params.format.getExtension()):"."+params.format.getExtension()));
                        ProcessDICOM(params, file.toString(), xdest.toString(), fis);
                    }
                }
                case DICOMDIR -> {
                    try (FileInputStream fis = new FileInputStream(file.toFile())) {
                        Path xdest = Paths.get(frag+(params.compress?String.format(".%s.gz",params.format.getExtension()):"."+params.format.getExtension()));
                        ProcessDICOM(params, file.toString(), xdest.toString(), fis);
                    }
                }
                case TAR -> {
                    try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new FileInputStream(file.toFile()))) {
                        ProcessTar(tarInput, Path.of(frag), file.toString());
                    }
                }
                default -> {
                    logger.log(Level.SEVERE, "Converting FAIL : {0}", file);
                    fc.incrementFailedConversionFileCount();
                }
            }
        } catch (Throwable t) {
            // Nothing reads the Future returned by submit(), so anything thrown here would vanish silently
            logger.log(Level.SEVERE, String.format("Conversion failed : %s ---> %s", t, file), t);
            fc.incrementFailedConversionFileCount();
        }
        return null;
    }
}
