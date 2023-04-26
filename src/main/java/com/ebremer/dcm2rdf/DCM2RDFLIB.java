package com.ebremer.dcm2rdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.json.JSONWriter;

/**
 *
 * @author erich
 */
public class DCM2RDFLIB {
    
    private JsonGenerator createGenerator(OutputStream out) {
        Map<String, Object> conf = new HashMap<>(2);
        conf.put(JsonGenerator.PRETTY_PRINTING, null);
        return Json.createGeneratorFactory(conf).createGenerator(out);
    }
    
    public String toJson(File file) {
        try (
            DicomInputStream dis = new DicomInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator jsonGen = createGenerator(baos);
        )
        {
            dis.setIncludeBulkData(IncludeBulkData.URI);
            dis.setBulkDataDirectory(null);
            dis.setBulkDataFilePrefix("blk");
            dis.setBulkDataFileSuffix(null);
            dis.setConcatenateBulkDataFiles(false);
            JSONWriter jsonWriter = new JSONWriter(jsonGen);
            dis.setDicomInputHandler(jsonWriter);
            dis.readDatasetUntilPixelData();
            jsonGen.flush();
            return baos.toString();
        } catch (IOException ex) {
            Logger.getLogger(DCM2RDFLIB.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("toJson(File file)...IOException ===> "+file.toString());
        }
        return null;
    }
      
    public Model FixMe(Model m) {
        if (m!=null) {
            String qs = "prefix : <http://dicom.nema.org/medical/dicom/ns#> select ?s ?SOPInstanceID where {?s :00080018/:Value ?SOPInstanceID}";
            Query query = QueryFactory.create(qs);
            QueryExecution qe = QueryExecutionFactory.create(query, m);
            ResultSet results = qe.execSelect();
            String s;
            String sop;
            Model y = ModelFactory.createDefaultModel();
            if (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                s = sol.get("s").asResource().toString();
                sop = sol.get("SOPInstanceID").asLiteral().getString();
                try {
                    sop = HashGeneratorUtils.generateMD5(sop);
                } catch (HashGenerationException ex) {
                    Logger.getLogger(DCM2RDFLIB.class.getName()).log(Level.SEVERE, null, ex);
                }
                sop = HashGeneratorUtils.Hex2Dec(sop);
                y.add(y.createLiteralStatement(y.createResource(s), y.createProperty("http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/md5/sophash"), sop));
                y.add(y.createLiteralStatement(y.createResource(s), y.createProperty("postgresql://posdadb.uhmc.sunysb.edu:5432/posda_files/posda_files/public/ns/file_meta/media_storage_sop_instance"), "1.3.6.1.4.1.14519.5.2.1.99.1071."+sop.substring(0, 32)));
                m.add(y);
                return m;
            } else {
                return m;
            } 
        } else {
            return null;
        }
    }
    
    public Model FixProcessDICOMFile(Path file) {
        String sha512Hash = null;
        try {
            sha512Hash = HashGeneratorUtils.generateSHA512(file.toFile());
        } catch (HashGenerationException ex) {
            Logger.getLogger(DCM2RDFLIB.class.getName()).log(Level.SEVERE, null, ex);
        }
        String md5Hash = null;
        try {
            md5Hash = HashGeneratorUtils.generateMD5(file.toFile());
        } catch (HashGenerationException ex) {
            Logger.getLogger(DCM2RDFLIB.class.getName()).log(Level.SEVERE, null, ex);
        }
        DCM2RDFLIB d2r = new DCM2RDFLIB();
        String json = d2r.toJson(file.toFile());
        //System.out.println(json);
        JsonReader jr = Json.createReader(new StringReader(json));
        Model m = null;
        try {
            JsonObject jo = jr.readObject();
            JSON2RDF j2r = new JSON2RDF("http://dicom.nema.org/medical/dicom/ns#");
            m = j2r.Process(jo,file.toUri().toString());
            //m.add(m.createResource(file.toUri().toString()), m.createProperty("http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/sha512"),sha512Hash);
            //m.add(m.createResource(file.toUri().toString()), m.createProperty("http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/md5"),md5Hash);
            //m.createLiteralStatement(m.createResource(file.toUri().toString()), m.createProperty("http://id.loc.gov/ontologies/bibframe/FileSize"),file.toFile().length());            
        } catch (javax.json.stream.JsonParsingException ex) {
            //Logger.getLogger(Dcm2RDF.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("ProcessDICOMFile..." +ex.getLocalizedMessage()+" ===> "+file.toString());
        }
        return m;
        //return FixMe(m);
    }
    
    public Model ProcessDICOMFile(Path file) {
        /*
        String sha512Hash = null;
        try {
            sha512Hash = HashGeneratorUtils.generateSHA512(file.toFile());
        } catch (HashGenerationException ex) {
            Logger.getLogger(Dcm2RDF.class.getName()).log(Level.SEVERE, null, ex);
        }*/
        String md5Hash = null;
        try {
            md5Hash = HashGeneratorUtils.generateMD5(file.toFile());
        } catch (HashGenerationException ex) {
            Logger.getLogger(DCM2RDFLIB.class.getName()).log(Level.SEVERE, null, ex);
        }
        DCM2RDFLIB d2r = new DCM2RDFLIB();
        String json = d2r.toJson(file.toFile());
        //System.out.println(json);
        JsonReader jr = Json.createReader(new StringReader(json));
        Model m = null;
        try {
            JsonObject jo = jr.readObject();
            JSON2RDF j2r = new JSON2RDF("http://dicom.nema.org/medical/dicom/ns#");
            m = j2r.Process(jo,file.toUri().toString());
            //m.add(m.createResource(file.toUri().toString()), m.createProperty("http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/sha512"),sha512Hash);
            m.add(m.createResource(file.toUri().toString()), m.createProperty("http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/md5"),md5Hash);
            m.addLiteral(m.createResource(file.toUri().toString()), m.createProperty("http://id.loc.gov/ontologies/bibframe/FileSize"),file.toFile().length());            
        } catch (javax.json.stream.JsonParsingException ex) {
            System.out.println("ProcessDICOMFile..." +ex.getLocalizedMessage()+" ===> "+file.toString());
        }
        return m;
    }
    
    public Model OptimizeRDF(Model m) {
        UpdateRequest request = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString ("""
            delete {
                ?s ?p ?cv .
                ?cv ?p ?o; :vr ?vr
            }
            where {
                ?s ?p ?cv .
                ?cv :vr ?vr
                minus {?cv (:Value|:InlineBinary) ?value}
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        request = UpdateFactory.create();
        pss = new ParameterizedSparqlString ("""
            delete where {
                ?s :00282000 ?o  # remove ICC profile
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete where {
                ?s :InlineBinary ?o  # remove InlineBinery
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete {
                ?bnd :Value ?Date .
                ?bnt :Value ?Time  
            }
            insert {
                ?bnd :Value ?newDate .
                ?bnt :Value ?DateTime
            }
            where {
                ?s :00080020 ?bnd . ?bnd :Value ?Date .
                ?s :00080030 ?bnt . ?bnt :Value ?Time .                           
                bind(xsd:date(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2))) as ?newDate)
                bind(xsd:dateTime(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2),"T",substr(?Time,1,2),":",substr(?Time,3,2),":",substr(?Time,5))) as ?DateTime)
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete {
                ?bnd :Value ?Date .
                ?bnt :Value ?Time  
            }
            insert {
                ?bnd :Value ?newDate .
                ?bnt :Value ?DateTime
            }
            where {
                ?s :00080021 ?bnd . ?bnd :Value ?Date .
                ?s :00080031 ?bnt . ?bnt :Value ?Time .                           
                bind(xsd:date(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2))) as ?newDate)
                                bind(xsd:dateTime(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2),"T",substr(?Time,1,2),":",substr(?Time,3,2),":",substr(?Time,5))) as ?DateTime)
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete {
                ?bnd :Value ?Date .
                ?bnt :Value ?Time  
            }
            insert {
                ?bnd :Value ?newDate .
                ?bnt :Value ?DateTime
            }
            where {
                ?s :00080022 ?bnd . ?bnd :Value ?Date .
                ?s :00080032 ?bnt . ?bnt :Value ?Time .                           
                bind(xsd:date(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2))) as ?newDate)
                bind(xsd:dateTime(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2),"T",substr(?Time,1,2),":",substr(?Time,3,2),":",substr(?Time,5))) as ?DateTime)
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete {
                ?bnd :Value ?Date .
                ?bnt :Value ?Time  
            }
            insert {
                ?bnd :Value ?newDate .
                ?bnt :Value ?DateTime
            }
            where {
                ?s :00080023 ?bnd . ?bnd :Value ?Date .
                ?s :00080033 ?bnt . ?bnt :Value ?Time .                           
                bind(xsd:date(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2))) as ?newDate)
                bind(xsd:dateTime(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2),"T",substr(?Time,1,2),":",substr(?Time,3,2),":",substr(?Time,5))) as ?DateTime)
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete {
                ?bnd :Value ?Date .
                ?bnt :Value ?Time  
            }
            insert {
                ?bnd :Value ?newDate .
                ?bnt :Value ?DateTime
            }
            where {
                ?s :00400244 ?bnd . ?bnd :Value ?Date .
                ?s :00400245 ?bnt . ?bnt :Value ?Time .                           
                bind(xsd:date(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2))) as ?newDate)
                bind(xsd:dateTime(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2),"T",substr(?Time,1,2),":",substr(?Time,3,2),":",substr(?Time,5))) as ?DateTime)
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
            delete {?bnd :Value ?Date}
            insert {?bnd :Value ?newDate}
            where {
                ?s :00100030 ?bnd . ?bnd :Value ?Date .                        
                bind(xsd:date(concat(substr(?Date,1,4),"-",substr(?Date,5,2),"-",substr(?Date,7,2))) as ?newDate)
            }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
                    delete {
                        ?s ?p ?cv .
                        ?cv :Value ?value; :vr ?vr
                    }
                    insert {
                        ?s ?p ?value
                    }
                    where {
                       ?s ?p ?cv .
                       ?cv :Value ?value; :vr ?vr
                    }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
                    delete {
                        ?s ?p ?cv .
                        ?cv :InlineBinary ?value; :vr ?vr
                    }
                    insert {
                        ?s ?p ?value
                    }
                    where {
                       ?s ?p ?cv .
                       ?cv :InlineBinary ?value; :vr ?vr
                    }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        pss = new ParameterizedSparqlString ("""
                    delete {
                        ?s :00101010 ?age
                    }
                    insert {
                        ?s :00101010 ?newage
                    }                                     
                    where {
                       ?s :00100030 ?bday; :00080020 ?StudyDate; :00101010 ?age
                       bind((year(xsd:date(?StudyDate))-year(?bday)) as ?newage )
                    }
        """);
        pss.setNsPrefix("xsd", XSD.NS);
        pss.setNsPrefix("", "http://dicom.nema.org/medical/dicom/ns#");
        request.add(pss.toString());
        UpdateAction.execute(request,m);
        return m;
    }
}
