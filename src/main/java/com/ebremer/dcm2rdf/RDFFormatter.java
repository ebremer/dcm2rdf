package com.ebremer.dcm2rdf;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author Erich Bremer
 */
public class RDFFormatter extends Formatter {
    public static final String NS = "https://halcyon.is/logger/ns/";
    
    @Override
    public String getHead(Handler h) {
        return String.format(
            """
            @prefix : %s .
            @prefix xsd: %s .
            
            """,
            NS,
            XSD.NS
        );       
    }

    @Override
    public String format(LogRecord record) {        
        String xsdDateTime = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            """
            <urn:uuid:%s>
                :message "%s";
                :sourceMethodName "%s.%s";
                :dateTime "%s"^^xsd:dateTime;
                :level :%s;
            """,
            UUID.randomUUID().toString(),
            formatMessage(record),
            record.getSourceClassName(),
            record.getSourceMethodName(),
            xsdDateTime,
            record.getLevel(),
            record.getSequenceNumber()
        ));         
        if (record.getParameters()!=null) {
            Arrays.stream(record.getParameters())
                .forEach(o->{
                    sb.append(String.format(
                        """
                            :parameter "%s";
                        """,o)
                    );
                });
        }
        sb.append(String.format(
            """
                :sequence %s .
            """, record.getSequenceNumber())
        );
        return sb.toString();
    }    
}
