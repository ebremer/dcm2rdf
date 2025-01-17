package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.PSS;
import com.ebremer.dcm2rdf.utils.HashGeneratorUtils;
import com.ebremer.dcm2rdf.utils.SHACL;
import com.ebremer.dcm2rdf.ns.GEO;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.ebremer.dcm2rdf.ns.PROVO;
import com.ebremer.dcm2rdf.ns.LOC;
import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.utils.Sha256CalculatingInputStream;
import com.ebremer.dcm2rdf.utils.Statistics;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
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
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTWriter;

/**
 *
 * @author erich
 */
public class DCM2RDFConverter {
    private final Parameters params;
    private static final Logger logger = java.util.logging.Logger.getLogger(dcm2rdf.class.getName());
    private Optional<String> hash = Optional.empty();
    
    public DCM2RDFConverter() {
        this(new Parameters());
    }
    
    public DCM2RDFConverter(Parameters params) {
        this.params = params;
    }
    
    public Optional<String> getHash() {
        return hash;
    }
    
    public Parameters getParameters() {
        return this.params;
    }
    
    public Model toModel(Resource root, Path file, byte[] bytes) {
        try ( DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(bytes)) ){
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
        if ( params.hash || params.naming.equals("SHA256") ) {
            try (
                Sha256CalculatingInputStream hashis = new Sha256CalculatingInputStream(is);
                DicomInputStream dis = new DicomInputStream(hashis)
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
                hashis.readAllBytes();
                this.hash = Optional.of(hashis.getSha256Hash());
                Statistics.getStatistics().AddFile(file.toFile().length(), 1);
                Statistics.getStatistics().AddActuallyRead(file.toFile().length());
            } catch (EOFException ex) {
                logger.log(Level.SEVERE, "End of File", root);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Problem with File {0}", root);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(DCM2RDFConverter.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            try (
                DicomInputStream dis = new DicomInputStream(is)
            ){         
                dis.setIncludeBulkData(IncludeBulkData.NO);
                RDFWriter rdfwriter = new RDFWriter(file, root);
                dis.setDicomInputHandler(rdfwriter);
                dis.readDatasetUntilPixelData();
                Statistics.getStatistics().AddFile(file.toFile().length(), 1);
                Statistics.getStatistics().AddActuallyRead(dis.getPosition());
            } catch (EOFException ex) {
                logger.log(Level.SEVERE, "End of File", root);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Problem with File {0}", root);
            }
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
        if (params.listurn) {
            m = ListURN(m);
        }
        return m;
    }  

    public Model ProcessDICOMasBytes2Model(Path file, InputStream is) {
        Model m = ModelFactory.createDefaultModel();     
        Resource root = m.createResource(String.format("urn:uuid:%s",UUID.randomUUID().toString()));
        root.addProperty(RDF.type, DCM.SOPInstance);
        toModel(root, file, is);
        if (params.hash) {
            if (hash.isPresent()) {
                root.addProperty(PROVO.wasDerivedFrom, m.createResource(String.format("urn:sha256:%s",hash.get())));
                root.addProperty(LOC.cryptographicHashFunctions.sha256, hash.get());
            } else {
                throw new Error("HASH not calculated : "+file.toString());
            }
        }
        if (params.extra) {
            m.setNsPrefix("bib", LOC.BibFrame.NS);
            m.setNsPrefix("cry", LOC.cryptographicHashFunctions.NS);
            try {
                URI xx = file.toUri();
                URI uri = new URI("file", "", xx.getPath(), null);
                root.addProperty(PROVO.wasDerivedFrom, m.createResource(uri.toString()));                
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), file);
            }
            root.addLiteral( LOC.BibFrame.FileSize, ResourceFactory.createTypedLiteral(String.valueOf(file.toFile().length()), XSDDatatype.XSDinteger ) );
        }
        m.setNsPrefix("dcm", DCM.NS);                        
        Optional<String> uid = getSOPInstanceUID(m);
        switch (params.naming) {
            case "SHA256" -> {
                if (hash.isPresent()) {                   
                    Resource vv = m.createResource(String.format("urn:sha256:%s",hash.get()));
                    m.removeAll(root, PROVO.wasDerivedFrom, vv);
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
    
    public Model ProcessDICOMasBytes2Model(Path file, byte[] bytes) {
        Model m = ModelFactory.createDefaultModel();     
        Resource root = m.createResource(String.format("urn:uuid:%s",UUID.randomUUID().toString()));
        Optional<String> sha256Hash = Optional.empty();
        if (params.extra) {
            m.setNsPrefix("bib", LOC.BibFrame.NS);
            m.setNsPrefix("cry", LOC.cryptographicHashFunctions.NS);
            sha256Hash = Optional.of(HashGeneratorUtils.generateSHA256(bytes));
            if (sha256Hash.isPresent()) {
                root.addProperty(PROVO.wasDerivedFrom, m.createResource(String.format("urn:sha256:%s",sha256Hash.get())));
                root.addProperty(LOC.cryptographicHashFunctions.sha256, sha256Hash.get());
            }
            try {
                URI xx = file.toUri();
                URI uri = new URI("file", "", xx.getPath(), null);
                root.addProperty(PROVO.wasDerivedFrom, m.createResource(uri.toString()));                
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
                    m.removeAll(root, PROVO.wasDerivedFrom, vv);
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
        Coordinate[] coords = coordinates.toArray(new Coordinate[0]);
        String wkt;
        GeometryFactory geometryFactory = new GeometryFactory();
        if (coords.length>1) {            
            LinearRing ring = geometryFactory.createLinearRing(coords);
            org.locationtech.jts.geom.Polygon polygon = geometryFactory.createPolygon(ring);
            WKTWriter wktWriter = new WKTWriter(3);
            wkt = wktWriter.write(polygon);
        } else {
            Point point = geometryFactory.createPoint(coords[0]);
            wkt = point.toText();
        }
        return rdfList.getModel().createTypedLiteral(wkt, GEO.NS+"wktLiteral");
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
    
    public Model ListURN(Model m) {
        // generate list URIs
        try {
            ParameterizedSparqlString pss =PSS.getPSS(  
                """            
                delete {
                    ?s ?tag ?list .
                    ?list
                        rdf:first ?first;
                        rdf:rest ?rest
                }
                insert {
                    ?s ?tag ?newlist .
                    ?newlist
                        rdf:first ?first;
                        rdf:rest ?rest
                }
                where {
                    ?s ?tag ?list .
                    ?list
                        rdf:first ?first;
                        rdf:rest ?rest
                    bind(iri(concat(str(?s), "/", STRAFTER(STR(?tag), ?ns))) as ?newlist)
                    filter(?tag!=rdf:first)
                    filter(?tag!=rdf:rest)
                    filter(?tag!=dcm:Value)
                }
                """
            );
            pss.setLiteral("ns", DCM.NS);
            UpdateAction.parseExecute(pss.toString(), m);
        } catch (Exception ha) {
            System.out.println(ha.getMessage());
        }
        return m;
    }
    
    public Model ListURN2(Model m) {
        // generate list URIs
        try {
            ParameterizedSparqlString pss = PSS.getPSS(  
                """            
                delete {
                    ?ss ?pp ?node .
                    ?node ?p ?o
                }
                insert {
                    ?ss ?pp ?newnode .
                    ?newnode ?p ?o
                }
                where {
                    ?root ?tag ?list .
                    ?list list:index (?index ?node) .
                    ?node ?p ?o .
                    ?ss ?pp ?node .
                    filter(isblank(?node))
                    filter(!isblank(?root))
                    bind(iri(concat(str(?root), "/", STRAFTER(STR(?tag), ?ns), "/", str(?index))) as ?newnode)
                    filter(strstarts(str(?tag), ?dcmNS))
                }
                """
            );
            pss.setLiteral("ns", DCM.NS);
            pss.setLiteral("dcmNS", DCM.NS);
            pss.setNsPrefix("list", "http://jena.apache.org/ARQ/list#");
            pss.setNsPrefix("d2r", dcm2rdf.NS);
            UpdateAction.parseExecute(pss.toString(), m);
        } catch (Exception ha) {
            System.out.println(ha.getMessage());
        }
        return m;
    }
}
