package com.ebremer.dcm2rdf;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public class Convert {
    
    private static final Pattern tmPattern = Pattern.compile("^(?:[01]\\d|2[0-3])(?:[0-5]\\d)?(?:[0-5]\\d(?:\\.\\d{1,6})?)?$");
    private static final Pattern daPattern = Pattern.compile("^(\\d{4})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])$");
    private static final Pattern dsPattern = Pattern.compile("^[+-]?\\d{0,15}(\\.\\d{1,15})?$");
    
    public static Literal toTime(String time) {
        String ptime = time.trim();
        if (tmPattern.matcher(ptime).matches()) {
            String[] frags = ptime.split("\\.");
            if (frags.length==1) {
                ptime = frags[0].substring(0, 2)+":"+frags[0].substring(2, 4)+":"+frags[0].substring(4, 6);
            } else {
                ptime = frags[0].substring(0, 2)+":"+frags[0].substring(2, 4)+":"+frags[0].substring(4, 6)+"."+frags[1];
            }
            return ResourceFactory.createTypedLiteral(ptime, XSDDatatype.XSDtime);
        }
        throw new Error("Invalid time string");
    }
    
    public static Literal toDate(String date) {
        String pdate = date.trim();
        if (daPattern.matcher(pdate).matches()) {
            return ResourceFactory.createTypedLiteral(pdate.subSequence(0, 4)+"-"+pdate.substring(4, 6)+"-"+pdate.substring(6), XSDDatatype.XSDdate);
        }
        throw new Error("Invalid date string [YYYYMMDD]");
    }

    public static Literal toDS(String src) {
        String psrc = src.trim();
        if (dsPattern.matcher(psrc).matches()) {
            return ResourceFactory.createTypedLiteral((new BigDecimal(psrc)).toPlainString(), XSDDatatype.XSDdecimal);
        }
        throw new Error("Invalid decimal string");
    }

    public static Literal toFL(String src) {
        try {
            return ResourceFactory.createTypedLiteral((Float.valueOf(src.trim())).toString(), XSDDatatype.XSDfloat);
        } catch (NumberFormatException ex) {
            throw new Error(ex.getMessage());
        }
    }
    
    public static Literal toFD(String src) {
        try {
            return ResourceFactory.createTypedLiteral((Double.valueOf(src.trim())).toString(), XSDDatatype.XSDdouble);
        } catch (NumberFormatException ex) {
            throw new Error(ex.getMessage());
        }
    }
    
    public static Literal toIS(String src) {
      //  try {
            return ResourceFactory.createTypedLiteral((Integer.valueOf(src.trim())).toString(), XSDDatatype.XSDinteger);
      //  } catch (NumberFormatException ex) {
        //    System.out.println(ex.getMessage());
        //    return ResourceFactory.createTypedLiteral(Integer.valueOf("0").toString(), XSDDatatype.XSDinteger);
            //return m.createResource()
              //  .addProperty(DCM.Value, src.trim())
                //.addProperty(RDF.type, DCM.Invalid);                
       // }
    }
}
