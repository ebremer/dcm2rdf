package com.ebremer.dcm2rdf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 * @author erich
 */
public class JSON2RDF {
    private final String ns;

    public JSON2RDF(String uri) {
        ns = uri;
    }
    
public Model Process(String json) {
    JsonReader jr = Json.createReader(new StringReader(json));
    return Process(jr.readObject());
}

Model Process(String json, String subject) {
    JsonReader jr = Json.createReader(new StringReader(json));
    return Process(jr.readObject(),subject);
}

public Model Process(JsonObject json, String subject) {
    Model m = ModelFactory.createDefaultModel();
    StringBuilder sb = new StringBuilder();
    sb.append("<").append(subject).append(">");
    sb.append(Digest(json,false));
    sb.insert(0, "@prefix : <"+ns+"> .\n");
    InputStream is = null;
    sb.append(" .");
    try {
        is = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
    } catch (UnsupportedEncodingException ex) {
        Logger.getLogger(JSON2RDF.class.getName()).log(Level.SEVERE, null, ex);
    }
    m.read(is, null, "TTL");
    return m;
}
    
public Model Process(JsonObject json) {
    Model m = ModelFactory.createDefaultModel();
    if (json==null) {
        System.out.println("Null object detected");
    } else {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        String rdf = Digest(json,false);
        sb.append(rdf);
        sb.append("]");
        sb.insert(0, "@prefix : <"+ns+"> .\n");
        InputStream is = null;
        sb.append(" .");
        try {
            is = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(JSON2RDF.class.getName()).log(Level.SEVERE, null, ex);
        }
        m.read(is, null, "TTL");
    }
    return m;
}

private String Digest(JsonObject seg, boolean blanknode) {
        StringBuilder sb = new StringBuilder();
        if (blanknode) sb.append("[");
        for (String field : seg.keySet()) {
            if (field==null) {
                System.out.println("NULL OBJECT DETECTION");
            } else {
            String rdffield = field.replaceAll("\\$", "_");
            Object oo = seg.get(field);
            if (field.equals("$date")) {
                JsonNumber ts = (JsonNumber) oo;
                long ts2 = ts.longValue();
                Date d = new Date(ts2);
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                String dd = "\""+formatter.format(d)+"\"^^<http://www.w3.org/2001/XMLSchema#dateTime>";
                sb.append(":").append(rdffield).append(" ").append(dd).append(";\n");
            } else if (oo instanceof JsonString jsonString) {
                String obj = jsonString.toString();
                sb.append(":").append(rdffield).append(" ").append(obj).append(";\n");
            } else if (oo instanceof JsonNumber jsonNumber) {
                String obj = jsonNumber.toString();
                sb.append(":").append(rdffield).append(" ").append(obj).append(";\n");            
            } else if (oo instanceof JsonObject jsonObject) {
                sb.append(":").append(rdffield).append(" ");
                sb.append(new JSON2RDF(ns).Digest(jsonObject, true));
                sb.append(";\n");
            } else if (oo instanceof JsonArray ja) {
                for (JsonValue av : ja) {
                    switch (av.getValueType()) {
                        case NUMBER:
                            sb.append(":").append(rdffield).append(" ").append(av.toString()).append("; ");
                            break;
                        case STRING:
                            sb.append(":").append(rdffield).append(" ").append(av.toString()).append("; ");
                            break;
                        case OBJECT:
                            sb.append(":").append(rdffield).append(" ").append(new JSON2RDF(ns).Digest((JsonObject) av,true)).append("; ");
                            break;
                        default:
                            //System.out.println("HAHAHAHA : "+field+"  "+av);
                            break;
                    }                
                }
            } else if (((JsonValue) oo).equals(JsonValue.NULL)) {
                //sb.append(":").append(rdffield).append(" \"null\"").append(";\n");
            } else {
                System.out.println("===============================================================");
                System.out.println("I don't know what the hell this is : "+oo.getClass());
                JsonValue jv = (JsonValue) oo;
                ValueType vt = jv.getValueType();
                System.out.println("YAYA1 : "+jv.getValueType());
                System.out.println("YAYA2 : "+jv.equals(JsonValue.NULL));
                System.out.println("YAYA3 : "+vt.name());
                String obj = oo.toString();
                System.out.println(field+"   "+obj);
                System.out.println("**********************************************************************");
            }}
        }
        if (blanknode) sb.append("]");
        return sb.toString();
    }
}