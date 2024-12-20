package com.ebremer.dcm2rdf;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.io.WKTWriter;

/**
 *
 * @author erich
 */
public class DCM2RDFConverter {
    private final Parameters params;
    private static final Logger logger = java.util.logging.Logger.getLogger(dcm2rdf.class.getName());
    
    public DCM2RDFConverter() {
        this(new Parameters());
    }
    
    public DCM2RDFConverter(Parameters params) {
        this.params = params;
    }
    
    public Parameters getParameters() {
        return this.params;
    }
    
    public Model toModel(Resource root, Path file, byte[] bytes) {
        try (
            InputStream targetStream = new ByteArrayInputStream(bytes);
            DicomInputStream dis = new DicomInputStream(targetStream);
        ){
            //dis.setIncludeBulkData(IncludeBulkData.URI);           
            dis.setIncludeBulkData(IncludeBulkData.NO);
            //dis.setBulkDataDirectory(null);
            //dis.setBulkDataFilePrefix("blk");
            //dis.setBulkDataFileSuffix(null);
            //dis.setConcatenateBulkDataFiles(false);
            RDFWriter rdfwriter = new RDFWriter(file, root);
            dis.setDicomInputHandler(rdfwriter);
            dis.readDatasetUntilPixelData();
        } catch (EOFException ex) {
            logger.log(Level.SEVERE, "End of File", file);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Problem with File {0}", file);
        }
        return root.getModel();
    }
    
    public Model toModel(Resource root, Path file, InputStream is) {
        try (
            DicomInputStream dis = new DicomInputStream(is);
        ){
            //dis.setIncludeBulkData(IncludeBulkData.URI);   
            dis.setIncludeBulkData(IncludeBulkData.NO);
            //dis.setBulkDataDirectory(null);
            //dis.setBulkDataFilePrefix("blk");
            //dis.setBulkDataFileSuffix(null);
            //dis.setConcatenateBulkDataFiles(false);
            RDFWriter rdfwriter = new RDFWriter(file, root);
            dis.setDicomInputHandler(rdfwriter);
            dis.readDatasetUntilPixelData();
        } catch (EOFException ex) {
            logger.log(Level.SEVERE, "End of File", root);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Problem with File {0}", root);
        }
        return root.getModel();
    }
    
    public Model Optimize(Model m) {        
        if (params.oid) {
            m = OptimizeRDF0(m);
        }
        if (!params.LongForm) {            
            m = OptimizeRDF1(m);
            m = OptimizeRDF2(m);
            m = OptimizeRDF3(m);
        }
        return m;
    }  

    public Model ProcessDICOMasBytes2Model(Path file, byte[] bytes) {
        Model m = ModelFactory.createDefaultModel();     
        Resource root = m.createResource(String.format("urn:uuid:%s",UUID.randomUUID().toString()));
        Optional<String> sha256Hash = Optional.empty();
        if (params.extra) {
            m.setNsPrefix("bib", LOC.BibFrame.NS);
            m.setNsPrefix("cry", LOC.cryptographicHashFunctions.NS);
            sha256Hash = Optional.of(HashGeneratorUtils.generateSHA256(bytes));
            if (sha256Hash.isPresent()) {
                root.addProperty(OWL.sameAs, m.createResource(String.format("urn:sha256:%s",sha256Hash.get())));
                root.addProperty(LOC.cryptographicHashFunctions.sha256, sha256Hash.get());
            }
            URI uri;
            try {
                URI xx = file.toUri();
                uri = new URI("file", "", xx.getPath(), null);
                root.addProperty(OWL.sameAs, m.createResource(uri.toString()));                
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), file);
            }
            root.addLiteral(LOC.BibFrame.FileSize, ResourceFactory.createTypedLiteral(String.valueOf(bytes.length), XSDDatatype.XSDinteger ));
        }
        m.setNsPrefix("dcm", DCM.NS);
        DCM2RDFConverter d2r = new DCM2RDFConverter(params);
        d2r.toModel(root,file,bytes);        
        root.addProperty(RDF.type, DCM.SOPInstance);
        m.setNsPrefix("dcm", DCM.NS);
        Optional<String> uid = getSOPInstanceUID(m);
        switch (params.naming) {
            case "SHA256" -> {
                if (sha256Hash.isPresent()) {                   
                    Resource vv = m.createResource(String.format("urn:sha256:%s",sha256Hash.get()));
                    m.removeAll(root, OWL.sameAs, vv);
                    FlipURI(root.toString(), vv.toString(), m);
                    
                } else {
                    throw new Error("File missing SOP Instance UID: "+file.toString());
                }
            }
            default -> {
                if (uid.isPresent()) {
                    FlipURI(root.toString(), "urn:oid:"+uid.get(), m);
                } else {
                    throw new Error("File missing SOP Instance UID: "+file.toString());
                }
            }     
        }
        return m;
    }
    
    public void FlipURI(String srcURI, String destURI, Model m) {
        UpdateRequest request = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete { ?old ?p ?o }
            insert { ?new ?p ?o }
            where { ?old ?p ?o }            
            """
        );
        pss.setIri("old", srcURI);
        pss.setIri("new", destURI);
        request.add(pss.toString());
        pss = new ParameterizedSparqlString(
            """
            delete { ?s ?p ?old }
            insert { ?s ?p ?new }
            where { ?s ?p ?old }
            """
        );
        pss.setIri("old", srcURI);
        pss.setIri("new", destURI);
        request.add(pss.toString());        
        UpdateAction.execute(request,m);
    }
    
    public Optional<String> getSOPInstanceUID(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
        """
        select ?uid
        where { ?s dcm:00080018/dcm:Value/rdf:first ?uid }
        limit 1
        """);
        pss.setNsPrefix("dcm", DCM.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), m).execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            RDFNode xuid = qs.get("uid");
            return Optional.of(xuid.asLiteral().toString());
        }
        return Optional.empty();
    }
    
    public Model OptimizeRDF1(Model m) {
        UpdateRequest request = UpdateFactory.create();
        // Remove Empty Fields
        request.add(PSS.get(
            """
            delete {
                ?s ?prop ?node .
                ?node dcm:vr ?vr
            }
            where {
                ?s ?prop ?node .
                ?node dcm:vr ?vr .
                minus {?node dcm:InlineBinary ?InlineBinary}
                minus {?node dcm:Value ?value}
                minus {?node dcm:BulkDataURI ?BulkDataURI}
                filter(dcm:isEvenDicomTag(?prop))
            }
            """));
        // Remove VRs 
        request.add(PSS.get(
            """
            delete {
                ?s ?prop ?node .
                ?node dcm:vr ?vr; dcm:Value ?value
            }
            insert {
                ?s ?prop ?value
            }
            where {
                ?s ?prop ?node .
                ?node dcm:vr ?vr; dcm:Value ?value
                filter(dcm:isEvenDicomTag(?prop))
            }
            """));        
        UpdateAction.execute(request,m);
        return m;
    }
    
    public Model OptimizeRDF2(Model m) {
        // remove rdf:List where VM is always 1
        Dataset ds = DatasetFactory.create();
        ds.getDefaultModel().add(m);
        ds.addNamedModel("https://ebremer.com/dummy/shacl", SHACL.getInstance().getModel());
        
        /*
        String cmd = PSS.get(
            """
            select distinct ?s ?tag ?first
            where {
                ?s ?tag ?list .
                ?list rdf:first ?first; rdf:rest rdf:nil
                minus {?otherlist rdf:rest ?list }
                {select distinct ?tag where { graph <https://ebremer.com/dummy/shacl> {?k sh:path ?tag; sh:maxCount 1 }}}
            }
            """);
        try (QueryExecution qexec = QueryExecutionFactory.create(cmd, ds)) {
            ResultSet results = qexec.execSelect();
            ResultSetFormatter.out(System.out, results);
        }   
        
        String c = PSS.get(
            """
            construct {
                ?s ?tag ?first
            }
            where {
                ?s ?tag ?list .
                ?list rdf:first ?first; rdf:rest rdf:nil
                minus {?otherlist rdf:rest ?list }
                {select distinct ?tag where { graph <https://ebremer.com/dummy/shacl> {?k sh:path ?tag; sh:maxCount 1 }}}
            }
            """);
        try (QueryExecution qexec = QueryExecutionFactory.create(c, ds)) {
            Model wow = qexec.execConstruct();
            wow.write(System.out, "TTL");
        } 
        */
        UpdateRequest request = UpdateFactory.create();
        request.add(PSS.get(
            """
            delete {
                ?s ?tag ?list .
                ?list rdf:first ?first; rdf:rest rdf:nil
            }
            insert {
                ?s ?tag ?first
            }
            where {
                ?s ?tag ?list .
                ?list rdf:first ?first; rdf:rest rdf:nil
                minus {?otherlist rdf:rest ?list }
                {select distinct ?tag where { graph <https://ebremer.com/dummy/shacl> {?k sh:path ?tag; sh:maxCount 1 }}}
            }
            """));
        //System.out.println("===================================================================================================");        
        UpdateAction.execute(request,ds);
        Model yah = ds.getDefaultModel();
        yah.setNsPrefix("dcm", DCM.NS);
        yah.setNsPrefix("xsd", XSD.NS);
        //RDFDataMgr.write(System.out, yah, Lang.TURTLE);       
        return yah;
    }
    
    public static void removeList(Resource listHead, Model model) {
        Resource current = listHead;
        while (!current.equals(RDF.nil)) {
            Statement firstStmt = current.getProperty(RDF.first);
            if (firstStmt != null) {
                model.remove(firstStmt);
            }
            Statement restStmt = current.getProperty(RDF.rest);
            if (restStmt != null) {
                Resource next = restStmt.getObject().asResource();
                model.remove(restStmt);
                current = next;
            } else {
                break;
            }
        }
    }

    public static Literal convertRDFListXYZToWKT(RDFList rdfList) {
        List<Coordinate> coordinates = new ArrayList<>();
        RDFList current = rdfList;
        while (!current.isEmpty()) {
            RDFNode firstNode = current.getHead();
            RDFNode secondNode = current.getTail().getHead();
            RDFNode thirdNode = current.getTail().getHead();
            double x = firstNode.asLiteral().getDouble();
            double y = secondNode.asLiteral().getDouble();
            double z = thirdNode.asLiteral().getDouble();
            coordinates.add(new Coordinate(x, y, z));
            current = current.getTail().getTail().getTail();
        }
        if (!coordinates.isEmpty() && !coordinates.get(0).equals(coordinates.get(coordinates.size() - 1))) {
            coordinates.add(coordinates.get(0));
        }
        GeometryFactory geometryFactory = new GeometryFactory();
        LinearRing ring = geometryFactory.createLinearRing(coordinates.toArray(new Coordinate[0]));
        org.locationtech.jts.geom.Polygon polygon = geometryFactory.createPolygon(ring);
        WKTWriter wktWriter = new WKTWriter(3);
        String wow = wktWriter.write(polygon);        
        return rdfList.getModel().createTypedLiteral(wow, GEO.NS+"wktLiteral");
    }

    public static String convertRDFListXYToWKT(RDFList rdfList) {
        List<Coordinate> coordinates = new ArrayList<>();
        RDFList current = rdfList;
        while (!current.isEmpty()) {
            RDFNode firstNode = current.getHead();
            RDFNode secondNode = current.getTail().getHead();
            double x = firstNode.asLiteral().getDouble();
            double y = secondNode.asLiteral().getDouble();
            coordinates.add(new Coordinate(x, y));
            current = current.getTail().getTail();
        }
        if (!coordinates.isEmpty() && !coordinates.get(0).equals(coordinates.get(coordinates.size() - 1))) {
            coordinates.add(coordinates.get(0));
        }
        GeometryFactory geometryFactory = new GeometryFactory();
        LinearRing ring = geometryFactory.createLinearRing(coordinates.toArray(new Coordinate[0]));
        org.locationtech.jts.geom.Polygon polygon = geometryFactory.createPolygon(ring);
        WKTWriter wktWriter = new WKTWriter();
        String wow = wktWriter.write(polygon);
        return wow;
    }    
    
    public Model OptimizeRDF3(Model m) {
        // convert Polygons to OGC WKT Literals        
        m.listSubjectsWithProperty(DCM._30060050)
            .forEach(r->{
                String type = r.getRequiredProperty(DCM._30060042).getObject().asLiteral().getString();
                switch (type) {
                    case "CLOSED_PLANAR" -> {
                        RDFList list = m.getList(r.getRequiredProperty(m.createProperty(DCM.NS,"30060050")).getObject().asResource());
                        r.addLiteral( DCM._30060050, convertRDFListXYZToWKT(list));
                        removeList(list, m);
                        m.removeAll(null, DCM._30060050, list);
                    }
                    //default -> throw new Error("Unsupported Polygon type : "+type);
                }
            });
        return m;
    }
    
    public Model OptimizeRDF0(Model m) {
        // convert UR VR to OID    
        UpdateAction.parseExecute(
            PSS.get(  
                """            
                delete {
                    ?listNode rdf:first ?oidString
                }
                insert {
                    ?listNode rdf:first ?oid
                }
                where {
                    ?s ?tag [ dcm:Value ?list; dcm:vr "UI" ] .
                    ?list (rdf:rest*) ?listNode .
                    ?listNode rdf:first ?oidString
                    bind(iri(concat("urn:oid:",str(?oidString))) as ?oid)
                }
                """
            ), m);
        return m;
    }
}
