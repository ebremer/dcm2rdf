package com.ebremer.dcm2rdf.testing;

import com.ebremer.dcm2rdf.D2R;
import com.ebremer.dcm2rdf.ns.DCM;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author Erich Bremer
 */
public class Test {
    
    public static void main(String[] args) {
        JenaSystem.init();
        D2R.init();
        String NS = "https://ebremer.com/ns/";
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("rnd", NS);
        m.setNsPrefix("rdf", RDF.getURI());
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("cdt", "http://w3id.org/awslabs/neptune/SPARQL-CDTs/");
        List<Literal> list = new ArrayList<>();
        list.add(m.createTypedLiteral(2.2f));
        list.add(m.createTypedLiteral(3.2f));
        list.add(m.createTypedLiteral(4.2f));
        list.add(m.createTypedLiteral("random thing"));
        //list.add(null);
        list.add(m.createTypedLiteral(5.2f));
        list.add(m.createTypedLiteral(6.2f));
        list.add(m.createTypedLiteral(7.2f));
        list.add(m.createTypedLiteral(1701l));
        list.add(m.createTypedLiteral(8.2f));
        list.add(m.createTypedLiteral(9.2f));
        list.add(m.createTypedLiteral(1701d));
        list.add(m.createTypedLiteral(10.2f));
        list.add(m.createTypedLiteral(11.2f));

        RDFList rlist = m.createList(list.iterator());               
        Resource r = m.createResource("https://ebremer.com");
        r.addProperty(m.createProperty(NS, "someProperty"), rlist);
        m.write(System.out, "TTL");
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete {
                ?s rnd:someProperty ?list .
                ?listNode ?p ?o .
            }
            insert {
                ?s rnd:someProperty ?cdtList
            }
            where {                
                ?s rnd:someProperty ?list                
                bind (dcm:rdf2cdtList(?list) as ?cdtList)
                ?list rdf:rest* ?listNode .
                FILTER (?listNode != rdf:nil)
                ?listNode ?p ?o .
            }
            """);
        pss.setNsPrefix("rdf", RDF.getURI());
        pss.setNsPrefix("dcm", DCM.NS);
        pss.setNsPrefix("rnd", NS);
        UpdateAction.execute(UpdateFactory.create(pss.toString()), m);
        m.write(System.out, "TTL");
    }
    
}
