package com.ebremer.dcm2rdf.utils;

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

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"'  -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }

    @Override
    public String getHead(Handler h) {
        return String.format(
            """
            @prefix : <%s> .
            @prefix xsd: <%s> .

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
            escape(formatMessage(record)),
            escape(record.getSourceClassName()),
            escape(record.getSourceMethodName()),
            xsdDateTime,
            record.getLevel()
        ));
        if (record.getParameters()!=null) {
            Arrays.stream(record.getParameters())
                .forEach(o->{
                    sb.append(String.format(
                        """
                            :parameter "%s";
                        """, escape(o == null ? "" : o.toString()))
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
