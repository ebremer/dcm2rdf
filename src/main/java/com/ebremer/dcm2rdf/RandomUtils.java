package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.GEO;
import com.ebremer.dcm2rdf.ns.PROVO;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOMDIR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DIRECTORY;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.TAR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.UNKNOWN;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;


public class RandomUtils {
    
    public static long GetTotal() {
        Runtime rt = Runtime.getRuntime();
        long left = rt.totalMemory()/(1024L*1024L*1024L);
        return left;
    }
    
    public static long GetMax() {
        Runtime rt = Runtime.getRuntime();
        long left = rt.maxMemory()/(1024L*1024L*1024L);
        return left;
    }
    
    public static long GetFree() {
        Runtime rt = Runtime.getRuntime();
        long left = rt.freeMemory()/(1024L*1024L*1024L);
        return left;
    }
    
    public static DirectoryProcessor.FileType getFileType(String file) {
        String r = file.toLowerCase();
        if ( r.endsWith(".dcm") || r.endsWith(".dat") ) {
            return DICOM;
        } else if (r.endsWith(".tar")) {
            return TAR;
        }
        return UNKNOWN;       
    }
    
    public static DirectoryProcessor.FileType getFileType(Path file) {
        if (file.toFile().isDirectory()) {
            return DIRECTORY;
        } else {
            String r = file.toString().toLowerCase();        
            if ( r.endsWith(".dcm") || r.endsWith(".dat") ) {
                return DICOM;
            } else if (file.getFileName().toString().equals("DICOMDIR")) {
                return DICOMDIR;
            } else if (r.endsWith(".tar")) {
                return TAR;
            }
            return UNKNOWN;
        }
    }

    public static void DumpModel(Model m, Path file, boolean compress) {
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("prov", PROVO.NS);
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("geo", GEO.NS);
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