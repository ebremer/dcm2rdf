package com.ebremer.dcm2rdf.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.vocabulary.SHACLM;

/**
 *
 * @author erich
 */
public class SHACL {
    private HashSet<String> onlyone;
    private Model m;
    
    private SHACL() {        
        m = ModelFactory.createDefaultModel();
        m.setNsPrefix("sh", SHACLM.NS);
        try (InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("shacl.ttl")) {
            RDFDataMgr.read(m, fis, Lang.TURTLE);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SHACL.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SHACL.class.getName()).log(Level.SEVERE, null, ex);
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?tag
            where {
                ?s a sh:PropertyShape; sh:path ?tag; sh:maxCount 1
            }
            """
        );
        onlyone = new HashSet<>();
        pss.setNsPrefix("sh", SHACLM.NS);
        QueryExecutionFactory.create(pss.toString(),m).execSelect().forEachRemaining(qs->{
            String tag = qs.get("tag").asResource().getURI();
            if (!onlyone.contains(tag)) {
                onlyone.add(tag);
            }
        });
    }
    
    public Model getModel() {
        return m;
    }

    public static SHACL getInstance() {
        return new SHACL();
    }
    
    public HashSet<String> OnlyOne() {
        onlyone.forEach(s->{
            System.out.println("Only One : "+s);
        });
        return onlyone;
    }
}
