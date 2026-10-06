package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.PSS;
import com.ebremer.dcm2rdf.utils.SHACL;
import com.ebremer.dcm2rdf.ns.GEO;
import com.ebremer.dcm2rdf.parameters.Parameters;
import com.ebremer.dcm2rdf.ns.PROVO;
import com.ebremer.dcm2rdf.ns.LOC;
import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.utils.Sha256CalculatingInputStream;
import com.ebremer.dcm2rdf.utils.Statistics;
import com.ebremer.dcm2rdf.utils.Tools;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.TagUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTWriter;

/**
 * Converts one DICOM instance to RDF and applies the post-conversion tweaks.
 * <p>
 * Internal: applications embedding dcm2rdf should use {@link Dcm2RdfBuilder}, whose API is the
 * supported one. This class may change without notice.
 *
 * @author erich
 */
public class DICOM2RDF {
    private final Parameters params;
    private static final Logger logger = Logger.getLogger(DICOM2RDF.class.getName());
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern OID = Pattern.compile("[0-9]+(\\.[0-9]+)*");
    // run statistics to record the bytes read into; null outside a CLI run
    private final Statistics stats;
    private Optional<String> hash = Optional.empty();
    
    public DICOM2RDF(Parameters params) {
        this(params, null);
    }

    /** As {@link #DICOM2RDF(Parameters)}, also recording the bytes read into stats. */
    public DICOM2RDF(Parameters params, Statistics stats) {
        this.params = params;
        this.stats = stats;
        D2R.init();
    }
    
    // A tag's predicate is hex-based, or keyword-based when -keywords is on; post-processing must match both
    private List<Property> tagProperties(int tag) {
        List<Property> props = new ArrayList<>();
        props.add(ResourceFactory.createProperty(DCM.NS, TagUtils.toHexString(tag)));
        if (params.keywords) {
            String keyword = ElementDictionary.keywordOf(tag, null);
            if (keyword != null && !keyword.isEmpty()) {
                props.add(ResourceFactory.createProperty(DCM.NS, keyword));
            }
        }
        return props;
    }

    // file is the logical name of the source - a path, a URI, or <archive>#<entry> - and is never
    // resolved against the filesystem here. The caller knows the source's size and records it
    // in Statistics; only the bytes actually read are recorded here.
    // Throws UncheckedIOException if the source can't be read as DICOM: a partial parse
    // (e.g. a truncated file) must not pass for a complete conversion
    private void read(Path src, Resource root, String file, InputStream is) {
        if ( params.hash || params.naming.equals("SHA256") ) {
            try {
                Sha256CalculatingInputStream hashis = new Sha256CalculatingInputStream(is);
                DicomInputStream dis = new DicomInputStream(hashis);
                dis.setIncludeBulkData(IncludeBulkData.NO);
                RDFWriter rdfwriter = new RDFWriter(src, file, root, params);
                dis.setDicomInputHandler(rdfwriter);
                dis.readDatasetUntilPixelData();
                hashis.readAllBytes();
                this.hash = Optional.of(hashis.getSha256Hash());
                if (stats != null) {
                    stats.addBytesRead(hashis.getByteCount());
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Cannot read DICOM %s", file), ex);
            } catch (NoSuchAlgorithmException ex) {
                // SHA-256 is a mandatory JCA algorithm; a JVM without it is unusable here
                throw new IllegalStateException("SHA-256 unavailable", ex);
            }
        } else {
            try {
                DicomInputStream dis = new DicomInputStream(is);  
                dis.setIncludeBulkData(IncludeBulkData.NO);
                RDFWriter rdfwriter = new RDFWriter(src, file, root, params);
                dis.setDicomInputHandler(rdfwriter);
                dis.readDatasetUntilPixelData();
                if (stats != null) {
                    stats.addBytesRead(dis.getPosition());
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Cannot read DICOM %s", file), ex);
            }
        }
    }

    // A source name that is already an absolute URI (s3://..., urn:...) is used as is; anything
    // else is a file path. A one-letter scheme is a Windows drive (C:/...), not a URI.
    private static Optional<URI> asAbsoluteURI(String name) {
        try {
            URI uri = new URI(name);
            return (uri.getScheme() != null && uri.getScheme().length() > 1) ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    /**
     * Converts one DICOM instance read from is, and names it as the options say. file is the
     * source's logical name: a path, a URI, or archive#member.
     *
     * @throws UncheckedIOException if the source can't be read as DICOM
     * @throws IllegalStateException if the source lacks what its naming needs
     */
    public Model convert(Path src, String file, InputStream is) {
        Model m = ModelFactory.createDefaultModel();     
        Resource root = m.createResource(String.format("urn:uuid:%s",UUID.randomUUID().toString()));
        root.addProperty(RDF.type, DCM.SOPInstance);
        read(src, root, file, is);
        if (params.hash) {
            if (hash.isPresent()) {
                root.addProperty(PROVO.wasDerivedFrom, m.createResource(String.format("urn:sha256:%s",hash.get())));
                root.addProperty(LOC.cryptographicHashFunctions.sha256, hash.get());
            } else {
                throw new IllegalStateException("HASH not calculated : "+file);
            }
        }
        if (params.extra) {
            m.setNsPrefix("bib", LOC.BibFrame.NS);
            m.setNsPrefix("cry", LOC.cryptographicHashFunctions.NS);
            // Archive members are addressed as <archive>#<entry>; the first '#' separates the two.
            // Path.toUri()/the URI constructor handle all percent-encoding (spaces, '#' in entry names, ...)
            String[] parts = file.split("#", 2);
            Optional<URI> named = asAbsoluteURI(parts[0]);
            URI uri;
            if (parts.length == 1) {
                uri = named.orElseGet(() -> Path.of(file).toUri());
            } else {
                URI xuri = named.orElseGet(() -> Path.of(parts[0]).toUri());
                try {
                    uri = named.isPresent()
                        ? new URI(xuri.getScheme(), xuri.getSchemeSpecificPart(), parts[1])
                        : new URI(xuri.getScheme(), "", xuri.getPath(), parts[1]);
                } catch (URISyntaxException ex) {
                    throw new IllegalArgumentException("Problem with file : "+file, ex);
                }
            }
            root.addProperty(PROVO.wasDerivedFrom, m.createResource(uri.toString()));
            if (parts.length == 1 && named.isEmpty()) {
                File srcFile = new File(file);
                if (srcFile.isFile()) {
                    root.addLiteral( LOC.BibFrame.FileSize, ResourceFactory.createTypedLiteral(String.valueOf(srcFile.length()), XSDDatatype.XSDinteger ) );
                }
            }
        }
        m.setNsPrefix("dcm", DCM.NS);                        
        Optional<String> uid = getNamingUID(m);
        switch (params.naming) {
            case "SHA256" -> {
                if (hash.isPresent()) {                   
                    Resource vv = m.createResource(String.format("urn:sha256:%s",hash.get()));
                    m.removeAll(root, PROVO.wasDerivedFrom, vv);
                    ResourceUtils.renameResource(root, vv.getURI());
                } else {
                    throw new IllegalStateException("SHA256 hash not calculated for "+file);
                }
            }
            default -> {
                if (uid.isEmpty()) {
                    throw new IllegalStateException("File missing SOP Instance UID: "+file);
                }
                // anything but an OID would make an invalid IRI, or one that is not what it claims
                if (!OID.matcher(uid.get()).matches()) {
                    throw new IllegalStateException(String.format(
                        "SOP Instance UID '%s' is not an OID and can't name the instance (-naming SHA256 can): %s", uid.get(), file));
                }
                ResourceUtils.renameResource(root, "urn:oid:"+uid.get());
            }     
        }
        return m;
    }    
    
    // the same conversion as the stream form, from bytes in memory
    public Model convert(Path file, byte[] bytes) {
        return convert(file, String.valueOf(file), new ByteArrayInputStream(bytes));
    }
    
    /**
     * Applies the standard post-conversion tweaks and optimizations selected in
     * {@link Parameters} to a model produced by {@link #convert}. This is the
     * same pipeline the command line applies to each converted file. A no-op when
     * longForm is set. May return a different Model instance than the one passed in.
     */
    public Model applyPostProcessing(Model m) {
        if (!params.longForm) {
            if (params.oid) {
                m = uidsToOids(m);
            }
            m = removeEmptyAttributesAndVRs(m);
            m = unwrapSingleValues(m);
            if (params.wkt) {
                m = contoursToWkt(m);
            }
            if (params.detlef) {
                m = nameSequenceItems(m);
            }
            if (params.cdt) {
                m = listsToCdt(m);
                m.setNsPrefix("cdt", "http://w3id.org/awslabs/neptune/SPARQL-CDTs/");
            }
            if (params.ptags) {
                m = groupPrivateElements(m);
            }
            if (params.sbu || params.padleftzero) {
                padPatientIds(m);
            }
        }
        return m;
    }

    public Model listsToCdt(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?list
            where {
                ?uri ?p ?list .
                filter (isblank(?list))
                filter(strstarts(str(?p),?prefix))
                ?list rdf:first ?item .
                # a blank member (dcm:Null, a person name) would be written into the literal as
                # a blank node label, meaningless outside this model
                filter not exists { ?list rdf:rest*/rdf:first ?member filter(isblank(?member)) }
                ?list list:length ?length                
                filter (?length>=?len)
            }
            """);
        pss.setLiteral("prefix", DCM.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("list", "http://jena.apache.org/ARQ/list#");
        pss.setLiteral("len", params.cdtlevel);        
        ResultSet rs;
        try (QueryExecution qe = QueryExecution.model(m).query(pss.toString()).build()) {
            rs = qe.execSelect().materialise();
        }
        rs.forEachRemaining(qs->{
            UpdateRequest request = UpdateFactory.create();
            ParameterizedSparqlString pssx = new ParameterizedSparqlString(
                """
                delete {
                    ?s ?xp ?listx .
                    ?listNode ?p ?o
                }
                insert {
                    ?s ?xp ?cdtList
                }
                where {                
                    ?s ?xp ?list .
                    ?s ?xp ?listx .
                    bind (dcm:rdf2cdtList(?list) as ?cdtList)
                    ?list rdf:rest* ?listNode .
                    FILTER (?listNode != rdf:nil)
                    ?listNode ?p ?o
                };
                """
            );
            pssx.setIri("list", qs.get("list").asResource().toString());
            pssx.setNsPrefix("dcm", DCM.NS);
            pssx.setNsPrefix("rdf", RDF.getURI());
            request.add(pssx.toString());
            UpdateAction.execute(request,m);
        });
        return m;
    }
    
    // A DICOMDIR's dataset has no SOP Instance UID; its instance is identified by the file meta
    // Media Storage SOP Instance UID, which is the fallback for any dataset lacking (0008,0018)
    private Optional<String> getNamingUID(Model m) {
        return getSOPInstanceUID(m).or(() -> getMediaStorageSOPInstanceUID(m));
    }

    public Optional<String> getSOPInstanceUID(Model m) {
        return getUID(m, "00080018", "SOPInstanceUID");
    }

    public Optional<String> getMediaStorageSOPInstanceUID(Model m) {
        return getUID(m, "00020003", "MediaStorageSOPInstanceUID");
    }

    // first value of a UI attribute of the SOP instance itself (not of a sequence item), under
    // either its hex or its keyword predicate
    private Optional<String> getUID(Model m, String hexTag, String keyword) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(String.format(
        """
        select ?uid
        where {
            ?s a dcm:SOPInstance .
            { ?s dcm:%s/dcm:Value/rdf:first ?uid }
            union
            { ?s dcm:%s/dcm:Value/rdf:first ?uid }
        }
        limit 1
        """, hexTag, keyword));
        pss.setNsPrefix("dcm", DCM.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        try (QueryExecution qe = QueryExecution.model(m).query(pss.toString()).build()) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                QuerySolution qs = rs.next();
                RDFNode xuid = qs.get("uid");
                return Optional.of(xuid.asLiteral().getString());
            }
        }
        return Optional.empty();
    }
    
    public Model removeEmptyAttributesAndVRs(Model m) {
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
                minus {?node dcm:InlineBinaryOmitted ?InlineBinaryOmitted}
                minus {?node dcm:BulkDataOmitted ?BulkDataOmitted}
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
    
    // The predicates of the data elements with VM 1, in hex and keyword form, as the bundled shapes
    // declare them; read once rather than joined against the shapes for every file. Sequences are
    // left out: an SQ's VM is 1, yet it holds any number of items, and unwrapping only the 1-item
    // ones would give sequences two different shapes.
    private static final class SingleValued {
        private static final Set<String> PREDICATES = load();

        private static Set<String> load() {
            Set<String> predicates = new HashSet<>();
            String query = PSS.get(
                """
                select distinct ?p
                where {
                    ?p a rdf:Property ; dcm:vm "1" .
                    filter not exists { ?p dcm:vr "SQ" }
                }
                """);
            try (QueryExecution qe = QueryExecution.model(SHACL.getInstance().getModel()).query(query).build()) {
                qe.execSelect().forEachRemaining(qs -> predicates.add(qs.getResource("p").getURI()));
            }
            return Set.copyOf(predicates);
        }
    }

    private static boolean isSingleValued(Property p) {
        return SingleValued.PREDICATES.contains(p.getURI());
    }

    public Model unwrapSingleValues(Model m) {
        // remove rdf:List where VM is always 1: a one-element list that is not the tail of another list
        List<Statement> wrapped = new ArrayList<>();
        m.listStatements().forEachRemaining(st -> {
            if (st.getObject().isResource() && isSingleValued(st.getPredicate())) {
                Resource list = st.getResource();
                if (list.hasProperty(RDF.first) && list.hasProperty(RDF.rest, RDF.nil) && !m.contains(null, RDF.rest, list)) {
                    wrapped.add(st);
                }
            }
        });
        for (Statement st : wrapped) {
            Resource list = st.getResource();
            Statement first = list.getRequiredProperty(RDF.first);
            m.add(st.getSubject(), st.getPredicate(), first.getObject());
            m.remove(st);
            m.remove(first);
            m.remove(list, RDF.rest, RDF.nil);
        }
        m.setNsPrefix("dcm", DCM.NS);
        m.setNsPrefix("xsd", XSD.NS);
        return m;
    }
    
    private static void removeList(Resource listHead, Model model) {
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
        
    // EPSG:7706 is an engineering CRS: a local, right-handed 3D Cartesian system in metres. It stands
    // in for the DICOM patient coordinate system (axes towards the patient's left, posterior and
    // head), so ContourData, given in millimetres, is divided by 1000.
    private static final String CONTOUR_CRS = "<http://www.opengis.net/def/crs/EPSG/0/7706> ";

    // POINT as POINT Z, OPEN_PLANAR and OPEN_NONPLANAR as LINESTRING Z, CLOSED_PLANAR and
    // CLOSEDPLANAR_XOR as POLYGON Z; null for any other type. Throws if the data doesn't fit the type.
    static Literal contourToWKT(String type, RDFList list) {
        List<RDFNode> values = list.asJavaList();
        if (values.isEmpty() || values.size() % 3 != 0) {
            throw new IllegalArgumentException(values.size() + " values are not (x, y, z) triplets");
        }
        Coordinate[] coords = new Coordinate[values.size() / 3];
        for (int i = 0; i < coords.length; i++) {
            coords[i] = new Coordinate(metres(values.get(3 * i)), metres(values.get(3 * i + 1)), metres(values.get(3 * i + 2)));
        }
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry geometry = switch (type) {
            case "POINT" -> {
                if (coords.length != 1) {
                    throw new IllegalArgumentException("a POINT contour with " + coords.length + " points");
                }
                yield geometryFactory.createPoint(coords[0]);
            }
            case "OPEN_PLANAR", "OPEN_NONPLANAR" -> geometryFactory.createLineString(coords);
            case "CLOSED_PLANAR", "CLOSEDPLANAR_XOR" -> {
                if (!coords[0].equals3D(coords[coords.length - 1])) {
                    coords = Arrays.copyOf(coords, coords.length + 1);
                    coords[coords.length - 1] = coords[0];
                }
                yield geometryFactory.createPolygon(coords);
            }
            default -> null;
        };
        if (geometry == null) {
            return null;
        }
        return list.getModel().createTypedLiteral(CONTOUR_CRS + new WKTWriter(3).write(geometry), GEO.NS + "wktLiteral");
    }

    // a DS value in mm; a dcm:Null or an invalid DS throws
    private static double metres(RDFNode value) {
        return value.asLiteral().getDouble() / 1000.0d;
    }

    public Model contoursToWkt(Model m) {
        // convert contours to OGC WKT literals; one whose data doesn't fit its type stays a list
        for (Property contourData : tagProperties(0x30060050)) {
            for (Resource r : m.listSubjectsWithProperty(contourData).toList()) {
                String type = null;
                for (Property geometricType : tagProperties(0x30060042)) {
                    Statement stmt = r.getProperty(geometricType);
                    if (stmt != null && stmt.getObject().isLiteral()) {
                        type = stmt.getString();
                        break;
                    }
                }
                RDFNode data = r.getRequiredProperty(contourData).getObject();
                if (type == null || !data.isResource() || !data.asResource().hasProperty(RDF.first)) {
                    continue;
                }
                RDFList list = data.as(RDFList.class);
                try {
                    Literal wkt = contourToWKT(type, list);
                    if (wkt != null) {
                        r.addLiteral(contourData, wkt);
                        removeList(list, m);
                        m.remove(r, contourData, list);
                    }
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "{0} contour left as a list: {1}", new Object[] {type, ex.getMessage()});
                }
            }
        }
        return m;
    }
    
    public Model padPatientIds(Model m) {
        // Pad numeric PatientIDs with zeros to make minimally 8 characters; other IDs are left as they are
        for (Property patientIDProp : tagProperties(0x00100020)) {
            m.listSubjectsWithProperty(patientIDProp).toList().forEach(r->{
                String patientID = r.getRequiredProperty(patientIDProp).getObject().asLiteral().getString();
                if (patientID.length()<8 && DIGITS.matcher(patientID).matches()) {
                    r.removeAll(patientIDProp);
                    r.addProperty(patientIDProp, Tools.padWithZeros(patientID));
                }
            });
        }
        return m;
    }
    
    public Model uidsToOids(Model m) {
        // convert UI values to urn:oid: IRIs. A value that isn't an OID stays a literal: it would
        // make an invalid IRI, or one that is not what it claims.
        List<Statement> uids = new ArrayList<>();
        for (Resource node : m.listSubjectsWithProperty(DCM.vr, "UI").toList()) {
            for (Statement value : node.listProperties(DCM.Value).toList()) {
                for (Resource cell = value.getResource(); cell != null && cell.hasProperty(RDF.first); cell = cell.getPropertyResourceValue(RDF.rest)) {
                    Statement first = cell.getRequiredProperty(RDF.first);
                    if (first.getObject().isLiteral() && OID.matcher(first.getString()).matches()) {
                        uids.add(first);
                    }
                }
            }
        }
        for (Statement first : uids) {
            first.changeObject(m.createResource("urn:oid:" + first.getString()));
        }
        return m;
    }
        
    public Model nameSequenceItems(Model m) {
        // Detlefication - generate Sequence URNs: a blank member of a list held by a named resource
        // gets <resource>#<tag>/<index>, and its own sequences are then named the same way, level by
        // level. Below the first level the path continues inside the fragment (<item>/<tag>/<index>),
        // as an IRI has only one '#'.
        Deque<Resource> named = new ArrayDeque<>(m.listSubjects().filterKeep(Resource::isURIResource).toList());
        while (!named.isEmpty()) {
            Resource parent = named.pop();
            String base = parent.getURI() + (parent.getURI().contains("#") ? "/" : "#");
            for (Statement st : parent.listProperties().toList()) {
                String tag = st.getPredicate().getURI();
                if (!tag.startsWith(DCM.NS) || !st.getObject().isAnon()) {
                    continue;
                }
                int index = 0;
                for (Resource cell = st.getResource(); cell != null && cell.hasProperty(RDF.first); cell = cell.getPropertyResourceValue(RDF.rest)) {
                    RDFNode member = cell.getRequiredProperty(RDF.first).getObject();
                    if (member.isAnon() && member.asResource().listProperties().hasNext()) {
                        String iri = base + tag.substring(DCM.NS.length()) + "/" + index;
                        named.push(ResourceUtils.renameResource(member.asResource(), iri));
                    }
                    index++;
                }
            }
        }
        return m;
    }
    
    public Model groupPrivateElements(Model m) {
        UpdateRequest request = UpdateFactory.create();
        /* see https://github.com/w3c/hcls-fhir-rdf/issues/145
        
            urn:oid:1.2.3.4.5 dcm:hasPrivateElement  [
                dcm:hasPrivateCreatorId "Private Creator ID";
                dcm:hasElement [
                    dcm:id "XX" ;
                    dcm:value "some arbitrary value" .
                ]
            ]  .
        */
        ParameterizedSparqlString pss = PSS.getPSS(
            """
            delete {
                ?s ?prop ?node .
                ?s ?PrivateCreatorURI ?bn .
                ?bn dcm:Value ?bnlist .
                ?bnlist rdf:first ?PrivateCreatorId .
                ?bnlist rdf:rest rdf:nil .
                ?bn dcm:vr ?bno
            }
            insert {
                ?s
                    dcm:hasPrivateElement ?xbn .
                    ?xbn
                        dcm:hasPrivateCreatorId ?PrivateCreatorId;
                        dcm:group ?group;
                        dcm:id ?id;
                        dcm:hasElement ?node .
                        ?node dcm:id ?pid                  
            }
            where {
                ?s
                    ?prop ?node;
                    ?PrivateCreatorURI ?bn;
                    a dcm:SOPInstance .
                    ?bn dcm:Value ?bnlist .
                    ?bnlist rdf:first ?PrivateCreatorId .
                    ?bnlist rdf:rest rdf:nil .
                    ?bn dcm:vr ?bno
                    filter(strstarts(str(?prop),?ptags))
                    filter(?PrivateCreatorURI != ?node)
                    bind(replace(str(?prop),?dcm,"") as ?tag)
                    bind(substr(?tag,1,4) as ?group)
                    bind(substr(?tag,7,2) as ?pid)
                    { select ?id ?ptags ?PrivateCreatorURI ?xbn where {                 
                            {   select distinct ?id ?ptags ?PrivateCreatorURI ?creatortag where {
                                    ?s ?prop ?node; a dcm:SOPInstance .            
                                    filter(dcm:isOddDicomTag(?prop))
                                    bind(replace(str(?prop),?dcm,"") as ?tag)
                                    bind(substr(?tag,1,4) as ?group)
                                    bind(substr(?tag,7,2) as ?id)
                                    bind(concat(?group,"00",?id) as ?creatortag)
                                    bind(concat(?dcm,?group,"00") as ?creator)
                                    bind(concat(?dcm,?group,?id) as ?ptags)
                                    bind(?prop as ?PrivateCreatorURI)
                                    filter(strstarts(str(?prop),?creator))                        
                                }
                            }
                        bind(bnode(?creatortag) as ?xbn)
                    }
                }
            }            
            """
        );
        pss.setLiteral("dcm", DCM.NS);
        request.add(pss.toString());
        UpdateAction.execute(request,m);
        return m;
    }    
}
