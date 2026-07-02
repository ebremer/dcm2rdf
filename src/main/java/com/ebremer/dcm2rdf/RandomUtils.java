package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.GEO;
import com.ebremer.dcm2rdf.ns.PROVO;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOMDIR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DIRECTORY;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.TAR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.UNKNOWN;
import com.ebremer.dcm2rdf.parameters.Parameters;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
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
        int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        if (file.substring(sep + 1).equals("DICOMDIR")) {
            return DICOMDIR;
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
    
    public static String StripExtension(String name) {
        if (name.endsWith(".dcm") || name.endsWith(".dat")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    public static void DumpModel(Model m, Path file, Parameters params) throws IOException {
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("prov", PROVO.NS);
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("geo", GEO.NS);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Write to a temp file and move it into place, so a failed run can't leave a
        // partial output that later runs mistake for a finished conversion
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            try (OutputStream fos = params.compress
                    ? new GZIPOutputStream(new FileOutputStream(tmp.toFile()))
                    : new FileOutputStream(tmp.toFile())) {
                RDFDataMgr.write(fos, m, params.format.getRDFFormat());
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException suppressed) {
                ex.addSuppressed(suppressed);
            }
            throw ex;
        }
    }
}