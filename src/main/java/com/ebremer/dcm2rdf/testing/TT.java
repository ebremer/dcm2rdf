package com.ebremer.dcm2rdf.testing;

import com.ebremer.dcm2rdf.D2R;
import com.ebremer.dcm2rdf.ns.DCM;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author Erich Bremer
 */
public class TT {
    
    public static void main(String[] args) {
        D2R.init();
        Model m = ModelFactory.createDefaultModel();
        try (FileInputStream fis = new FileInputStream("/dicom/1-1.ttl")) {
            RDFDataMgr.read(m, fis, Lang.TURTLE);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TT.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TT.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?uri ?p ?list ?length ?cdtList
            where {
                ?uri ?p ?list .
                filter (isblank(?list))
                filter(strstarts(str(?p),?prefix))                
                ?list rdf:first ?item .
                ?list list:length ?length                
                filter (?length>=3)
                bind (dcm:rdf2cdtList(?list) as ?cdtList)
            }
            """);
        pss.setLiteral("prefix", DCM.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("dcm", DCM.NS);
        pss.setNsPrefix("list", "http://jena.apache.org/ARQ/list#");
        //pss.setLiteral("len", params.cdtlevel);
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), m).execSelect().materialise();
        ResultSetFormatter.out(System.out, rs);
        
    }
    
}
