package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.Statistics;
import com.ebremer.dcm2rdf.utils.FileCounter;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.ebremer.dcm2rdf.DirectoryProcessor.FileType;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOMDIR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.TAR;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
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
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 * @author erich
 */
public class DirectoryProcessor {
    public enum FileType {DIRECTORY, DICOMDIR, DICOM, TAR, UNKNOWN};
    public Model buffer = ModelFactory.createDefaultModel();
    private final Parameters params;
    private final FileCounter fc;
    private final ProgressBar progressBar;
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
    
    public synchronized void AddModel(Model m) {
        buffer.add(m);
    }

    public void Traverse(Parameters params, Set<FileType> allowedfiletypes, FileType ftype) {
        try (ThreadPoolExecutor engine = new ThreadPoolExecutor(params.threads, params.threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>())) {
            engine.prestartAllCoreThreads();
            Files.walk(params.src.toPath())                        
                .parallel()
                .filter(Objects::nonNull)
                .filter(p->{
                    if (p.toFile().isDirectory()) {
                        if (params.status) fc.incrementDirectoryCount();
                        return false;
                    }
                    return true;
                })
                .filter(p->{
                    FileType ft = RandomUtils.getFileType(p);
                    switch (ft) {
                        case FileType.DIRECTORY -> {
                            if (params.status) fc.incrementDirectoryCount();
                            return false;
                        }
                        case FileType.DICOM -> {
                            if (params.status) fc.incrementDicomFileCount();
                            return true;
                        }
                        case FileType.DICOMDIR -> {
                            if (params.status) fc.incrementDicomFileCount();
                            return true;
                        }
                        case FileType.TAR -> {
                            if (params.status) fc.incrementTarFileCount();
                            return true;
                        }
                        default -> {
                            if (params.status) fc.incrementOtherFileCount();
                            return false;                            
                        }
                    }
                })
                .filter(p->{
                    if (p.toFile().length()>0) {
                        return true;
                    }
                    logger.log(Level.SEVERE, "Zero Length File", p);
                    Statistics.getStatistics().AddFile(0, 1);
                    if (params.status) fc.incrementZeroLengthFileCount();
                    return false;                
                })
                .forEach(p->{
                    FileType ft = RandomUtils.getFileType(p);
                    if (params.status) {
                        progressBar.maxHint(fc.getDicomFileCount()+fc.getTarFileCount());
                        progressBar.stepTo(engine.getCompletedTaskCount());
                    }
                    engine.submit(new FileProcessor(params,fc,ft,p));
                });
            engine.shutdown();
            while (!engine.isTerminated()) {
                if (params.status) {
                    progressBar.stepTo(engine.getCompletedTaskCount());
                    progressBar.maxHint(fc.getDicomFileCount()+fc.getTarFileCount());
                }                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    logger.severe(ex.getMessage());
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (params.status) {
            System.out.println("\n"+fc);
        }
    }
    
    public void Protocol(FileType ftype) {
        Set<FileType> subset = EnumSet.of(FileType.DICOM, FileType.TAR);
        Traverse(params, subset, ftype);
    }
}

class FileProcessor implements Callable<Model> {
    private final Path file;
    private final Parameters params;
    private final FileCounter fc;
    private final DirectoryProcessor.FileType ft;
    private static final Logger logger = Logger.getLogger(dcm2rdf.class.getName());

    public FileProcessor(Parameters params, FileCounter fc, DirectoryProcessor.FileType ft, Path file) {
        this.params = params;
        this.fc = fc;
        this.ft = ft;
        this.file = file;
    }

    public Model ScanMeta(Parameters params, Path file, InputStream is) {
        DICOM2RDF d2r = new DICOM2RDF(params);     
        Model m = d2r.ProcessDICOMasBytes2Model(this.file, is);                 
        if (!params.LongForm) {
            if (params.oid) {
               m = d2r.OptimizeUR2URNOID(m);
            }
            m = d2r.OptimizeRemoveEmptyFieldandVRs(m);
            m = d2r.OptimizeRemoveRDFListWhenAlwaysOne(m);
            if (params.wkt) {
                m = d2r.OptimizePolygons2WKT(m);
            }
            if (params.detlef) {
                m = d2r.GenSeqNames(m);
            }
            d2r.PadLeftZero8(m);            
        }
        return m;
    }

    private void ProcessTar(TarArchiveInputStream tarInput, Path root) throws IOException {
        TarArchiveEntry ce = tarInput.getNextEntry();
        while (ce != null) {            
            if (ce.isDirectory()) {
                if (params.status) fc.incrementTarDirectoryCount();
            } else {
                if (ce.getSize()==0) {
                    if (params.status) fc.incrementZeroLengthFileCount();
                    logger.log(Level.SEVERE, "Zero Length File", Path.of(root.toString(), ce.getName()));
                } else {
                    FileType tft = RandomUtils.getFileType(ce.getName());
                    switch(tft) {
                        case DICOM -> {
                            if (params.status) fc.incrementTarDicomFileCount();
                            ProcessDICOM(params, Path.of(root.toString(), ce.getName()).toString(), tarInput);
                        }
                        case DICOMDIR -> {
                            if (params.status) fc.incrementTarDicomFileCount();
                            ProcessDICOM(params, Path.of(root.toString(), ce.getName()).toString(), tarInput);
                        }
                        case TAR -> {
                            if (params.status) fc.incrementTarFileCount();
                            ProcessTar(tarInput, Path.of(root.toString(), ce.getName()));
                        }
                        default -> fc.incrementTarOtherFileCount();
                    }
                }                
            }
            ce = tarInput.getNextEntry();
        }
    }
    
    private void ProcessDICOM(Parameters params, String root, InputStream is) {
        Path dest = Paths.get(root+(params.compress?".ttl.gz":".ttl"));
        if ( !dest.toFile().exists() || params.overwrite ) {
            Model m = ScanMeta(params, Path.of(root), is);
            if (dest.toFile().exists()) {
                dest.toFile().delete();
            }
            if ((m!=null)&&(m.size()!=0)) {                                
                RandomUtils.DumpModel(m,dest,params.compress);
            }
            if (!file.toFile().exists()) {
                System.out.println("Failed to create : "+file);
            }
        }
    }

    @Override
    public Model call() {
        String frag = Path.of(params.dest.toString(), params.src.toPath().relativize(file).toString()).toString();
        switch (ft) {
            case DICOM -> {
                try (FileInputStream fis = new FileInputStream(file.toFile())) {
                    frag = frag.substring(0, frag.length()-4);
                    Path xdest = Paths.get(frag+(params.compress?".ttl.gz":".ttl"));
                    if (!xdest.toFile().exists() || params.overwrite) {
                        ProcessDICOM(params, frag, fis);
                    }
                } catch (FileNotFoundException ex) {
                    logger.severe(ex.getMessage());
                } catch (IOException ex) {
                    logger.severe(ex.getMessage());
                }
            }
            case DICOMDIR -> {
                try (FileInputStream fis = new FileInputStream(file.toFile())) {
                    Path xdest = Paths.get(frag+(params.compress?".ttl.gz":".ttl"));
                    if (!xdest.toFile().exists() || params.overwrite) {
                        ProcessDICOM(params, frag, fis);
                    }
                } catch (FileNotFoundException ex) {
                    logger.severe(ex.getMessage());
                } catch (IOException ex) {
                    logger.severe(ex.getMessage());
                }
            }
            case TAR -> {         
                try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new FileInputStream(file.toFile()))) {
                    ProcessTar(tarInput, Path.of(frag));
                } catch (IOException ex) {
                    logger.severe(ex.getMessage());
                }
            }
            default -> {
                logger.log(Level.SEVERE, "Converting FAIL : {0}", file);
                fc.incrementFailedConversionFileCount();
            }
        }
        return null;
    }
}
