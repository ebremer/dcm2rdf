package com.ebremer.dcm2rdf.testing;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author Erich Bremer
 */
public class NewClass {
    
    public static void main(String[] args) {
        Model m = ModelFactory.createDefaultModel();        
        String cdt = "http://w3id.org/awslabs/neptune/SPARQL-CDTs/";
        String list = "http://w3id.org/awslabs/neptune/SPARQL-CDTs/List";
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("cdt", cdt);
        m.setNsPrefix("rdf", RDF.getURI());
        
        Resource r = m.createResource().addLiteral(RDF.value,
            m.createTypedLiteral("[1,'two',3,4,5]", list)
        ).addLiteral(RDF.value,
            m.createTypedLiteral("[[1,2],[-1.2,3],[100,2]]", list)
        )
       ;
        
        m.write(System.out, "TTL");
        
        Statement ss = r.getProperty(RDF.value);
        System.out.println(ss.getObject().asLiteral().getDatatypeURI());
        
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select *
            where {
                ?s ?p ?o
                bind( cdt:size(?o) as ?size )            
                bind( cdt:get(?o,2) as ?pos ) 
            }
            """);
        pss.setNsPrefix("cdt", cdt);
        pss.setNsPrefix("rdf", RDF.getURI());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), m).execSelect();
        ResultSetFormatter.out(System.out, rs);
    }
    
}
