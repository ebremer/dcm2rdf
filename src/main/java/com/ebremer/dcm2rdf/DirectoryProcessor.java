package com.ebremer.dcm2rdf;

import static com.ebremer.dcm2rdf.Utils.GetFree;
import static com.ebremer.dcm2rdf.Utils.GetMax;
import static com.ebremer.dcm2rdf.Utils.GetTotal;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

/**
 *
 * @author erich
 */
public class DirectoryProcessor {
    public enum FileType {DICOM, HL7, XML, CSV, DFIX};
    public Model buffer = ModelFactory.createDefaultModel();
    private final Parameters params;
    
    public DirectoryProcessor(Parameters params) {
        if (params.upd==null) {
            params.upd = params.dest;
        }
        this.params = params;
    }
    
    public synchronized void AddModel(Model m) {
        buffer.add(m);
    }
    
    public void Traverse(Path src, Path dest, Path updates, String[] ext, FileType ftype, boolean compress) {
    //    List<Future<Model>> futures = new ArrayList<>();
        ThreadPoolExecutor engine = new ThreadPoolExecutor(params.cores,params.cores,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
        engine.prestartAllCoreThreads();
        try(Stream<Path> yay = Files.walk(src)) {
            yay
                .filter(Objects::nonNull)
                .filter(fff -> {
                    for (String ext1 : ext) {
                        if (fff.toFile().toString().toLowerCase().endsWith(ext1)) {
                            return true;
                        }
                    } 
                    return false;
                })
                .forEach(p->{
                    //while (engine.getActiveCount()>1000) { 
                        //wait
                    //}
                    //Callable<Model> worker = 
                    engine.submit(new FileProcessor(params,p,ftype));
                    //futures.add();
                });
        } catch (IOException ex) {
            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
        int cc = 0;
        while ((engine.getActiveCount()+engine.getQueue().size())>0) {
            if ((cc%10000)==0) {
                System.out.println("All jobs submitted...waiting for "+(engine.getActiveCount()+engine.getQueue().size()));
                cc=0;
            }
            cc++;
        }
        System.out.println("Engine shutdown");
        engine.shutdown();
    }
    
    public static String[] GetExtensions(FileType ftype) {
        return switch (ftype) {
            case DICOM -> new String[] {"dcm", "DCM", "dat", "DAT"};
            default -> null;
        };
    }
    
    public void Protocol(FileType ftype) {
        System.out.println("Available # of cores : "+Runtime.getRuntime().availableProcessors());
        System.out.println( "free memory: " + GetFree()+" Total : "+GetTotal()+"  Max: "+GetMax() );
        System.out.println("Number of cores being used : "+params.cores);
        Traverse(params.src.toPath(),params.dest.toPath(), params.upd.toPath(), GetExtensions(ftype), ftype, params.compress);
        System.out.println("Done.");
    }
}

class FileProcessor implements Callable<Model> {
    private final Path file;
    private final DirectoryProcessor.FileType ftype;
    private final Parameters params;

    public FileProcessor(Parameters params, Path file, DirectoryProcessor.FileType ftype) {
        this.params = params;
        this.ftype = ftype;
        this.file = file;
    }
    
    @Override
    public Model call() {
        System.out.println("Processing "+file.toString());
        Model m = null;
        String frag = params.src.toPath().relativize(file).toString();
        if (frag.endsWith(".gz")) {
            frag = frag.substring(0, frag.length()-7)+".ttl";
        } else {
            frag = frag.substring(0, frag.length()-4)+".ttl";
        }
        if (params.compress) {
            frag = frag +".gz";
        }
        Path tar = Paths.get(params.dest.toPath().toString() + File.separatorChar+frag);
        if (tar.toFile().exists()&&tar.toFile().length()==0) {
            tar.toFile().delete();
        }
        if (!tar.toFile().exists()) {
            switch(ftype) {
                case DICOM:
                    DCM2RDFLIB d2r = new DCM2RDFLIB();
                    m = d2r.ProcessDICOMFile(file);
                    if (!params.LongForm) {
                        d2r.OptimizeRDF(m);
                    }
                    m.setNsPrefix("dcm", "http://dicom.nema.org/medical/dicom/ns#");
                    m.setNsPrefix("bib", "http://id.loc.gov/ontologies/bibframe/");
                    m.setNsPrefix("cry", "http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/");
                    break;
            }
            if (m!=null) {
                Path dump = Paths.get(params.upd.toPath().toString() + File.separatorChar+frag);                        
                DumpModel(m,dump,params.compress);
            }
        }
        return m;
    }
    
    public void DumpModel(Model m, Path file, boolean compress) {
        if (!file.getParent().toFile().exists()) {
            file.getParent().toFile().mkdirs();
        }
        if (compress) {
            try (OutputStream fos = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
                RDFDataMgr.write(fos, m, RDFFormat.TURTLE_PRETTY);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(FileProcessor.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(FileProcessor.class.getName()).log(Level.SEVERE, null, ex);
            } 
        } else {
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                RDFDataMgr.write(fos, m, RDFFormat.TURTLE_PRETTY);
            } catch (IOException ex) {
                Logger.getLogger(FileProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
