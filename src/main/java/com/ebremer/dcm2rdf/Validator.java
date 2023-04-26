package com.ebremer.dcm2rdf;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;

/**
 *
 * @author Erich Bremer
 */
public class Validator {
    
    public static void main(String[] args) {
        Model shacl = ModelFactory.createDefaultModel();
        Model m = ModelFactory.createDefaultModel();
        ClassLoader classLoader = Validator.class.getClassLoader();
        Shapes shapes;
        try (
            InputStream sis = classLoader.getResourceAsStream("shacl.ttl");
            InputStream tis = new GZIPInputStream( new FileInputStream("D:\\arie\\rdf\\00001607\\MRN-00001607-DATE-20191017-1614289564582.ttl.gz"));
        ) {
            RDFDataMgr.read(shacl, sis, Lang.TURTLE);
            RDFDataMgr.read(m, tis, Lang.TURTLE);
            System.out.println(shacl.size());
            System.out.println(m.size());
            shapes = Shapes.parse(shacl);           
            
            ValidationReport report = ShaclValidator.get().validate(shapes, m.getGraph());            
            if (report.conforms()) {
                System.out.println("Valid Graph Shape : "+report.conforms());
            } else {                
                ShLib.printReport(report);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
