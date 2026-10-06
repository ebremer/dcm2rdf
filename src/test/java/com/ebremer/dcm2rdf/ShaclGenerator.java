package com.ebremer.dcm2rdf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Generates {@code src/main/resources/shacl.ttl} from the data element registry of DICOM PS3.6,
 * the Data Dictionary (tables 6-1, 7-1, 8-1 and 9-1 of its DocBook source).
 * <p>
 * For every data element it writes:
 * <ul>
 * <li>{@code rdf:Property} declarations of the hex predicate and, when there is one, the keyword
 *     predicate (the two linked by {@code owl:equivalentProperty}), annotated with the element's
 *     name, keyword, VR(s) and VM. The converter reads {@code dcm:vm "1"} to know which values to
 *     take out of their {@code rdf:List};</li>
 * <li>a property shape that targets the subjects of those predicates and checks the value has
 *     the form dcm2rdf writes for the element's VR and VM - a value for VM 1, else an
 *     {@code rdf:List} of values - in the default output and with -keywords, -oid, -detlef,
 *     -cdt, -wkt, -ptags, -extra or -hash. The long form (-L) keeps every attribute's
 *     {@code dcm:vr}/{@code dcm:Value} wrapper and is not covered.</li>
 * </ul>
 * Repeating groups (50xx, 60xx, 7Fxx) are written out for each of their 16 groups, under hex
 * predicates only, as the converter writes them. Elements with other {@code x} placeholders
 * (all retired) and those without a VR are left out.
 * <p>
 * Usage: {@code java -cp <test classpath> com.ebremer.dcm2rdf.ShaclGenerator <part06.xml> <shacl.ttl>},
 * with part06.xml from https://dicom.nema.org/medical/dicom/current/source/docbook/part06/part06.xml
 */
public final class ShaclGenerator {

    private static final String DOCBOOK = "http://docbook.org/ns/docbook";
    private static final Set<String> REGISTRY_TABLES = Set.of("table_6-1", "table_7-1", "table_8-1", "table_9-1");
    private static final Pattern TAG = Pattern.compile("\\(([0-9A-Fa-fx]{4}),([0-9A-Fa-fx]{4})\\)");
    private static final Set<String> BINARY = Set.of("OB", "OD", "OF", "OL", "OV", "OW", "UN");

    private record DataElement(String group, String element, String name, String keyword, List<String> vrs, String vm, boolean retired) {
    }

    private ShaclGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: ShaclGenerator <part06.xml> <shacl.ttl>");
            System.exit(1);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(Path.of(args[0]).toFile());
        String edition = text((Element) doc.getElementsByTagNameNS(DOCBOOK, "subtitle").item(0))
            .replace(" - Data Dictionary", "");
        List<DataElement> elements = registry(doc);
        Files.writeString(Path.of(args[1]), turtle(edition, elements), StandardCharsets.UTF_8);
        System.out.println(elements.size() + " data elements from " + edition);
    }

    private static List<DataElement> registry(Document doc) {
        List<DataElement> elements = new ArrayList<>();
        NodeList tables = doc.getElementsByTagNameNS(DOCBOOK, "table");
        for (int t = 0; t < tables.getLength(); t++) {
            Element table = (Element) tables.item(t);
            if (!REGISTRY_TABLES.contains(table.getAttributeNS("http://www.w3.org/XML/1998/namespace", "id"))) {
                continue;
            }
            Element body = (Element) table.getElementsByTagNameNS(DOCBOOK, "tbody").item(0);
            NodeList rows = body.getElementsByTagNameNS(DOCBOOK, "tr");
            for (int r = 0; r < rows.getLength(); r++) {
                NodeList cells = ((Element) rows.item(r)).getElementsByTagNameNS(DOCBOOK, "td");
                Matcher tag = TAG.matcher(text((Element) cells.item(0)));
                String vr = text((Element) cells.item(3));
                if (!tag.matches() || vr.isEmpty() || vr.equals("See Note")) {
                    continue;
                }
                String note = cells.getLength() > 5 ? text((Element) cells.item(5)) : "";
                elements.add(new DataElement(
                    tag.group(1).toUpperCase(Locale.ROOT).replace('X', 'x'),
                    tag.group(2).toUpperCase(Locale.ROOT).replace('X', 'x'),
                    text((Element) cells.item(1)),
                    text((Element) cells.item(2)),
                    List.of(vr.split(" or ")),
                    text((Element) cells.item(4)),
                    note.startsWith("RET")));
            }
        }
        return elements;
    }

    // a cell's text, without the zero-width spaces the standard puts in long keywords
    private static String text(Element e) {
        return e.getTextContent().replace("​", "").replaceAll("\\s+", " ").trim();
    }

    private static String turtle(String edition, List<DataElement> elements) {
        StringBuilder out = new StringBuilder();
        out.append("""
            # DICOM data element shapes for dcm2rdf output.
            # Generated by com.ebremer.dcm2rdf.ShaclGenerator from %s - Data Dictionary.
            # Do not edit by hand: regenerate it (see ShaclGenerator).
            PREFIX cdt:   <http://w3id.org/awslabs/neptune/SPARQL-CDTs/>
            PREFIX dcm:   <https://halcyon.is/dicom/ns/>
            PREFIX dcmsh: <https://halcyon.is/dicom/ns/Shape/>
            PREFIX geo:   <http://www.opengis.net/ont/geosparql#>
            PREFIX owl:   <http://www.w3.org/2002/07/owl#>
            PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX sh:    <http://www.w3.org/ns/shacl#>
            PREFIX xsd:   <http://www.w3.org/2001/XMLSchema#>

            <https://halcyon.is/dicom/ns/Shape/>
                a owl:Ontology ;
                rdfs:label "dcm2rdf DICOM data element shapes" ;
                rdfs:comment "One property shape per DICOM data element, checking the form dcm2rdf writes its value in. The long form (-L) is not covered." ;
                owl:versionInfo "%s" .

            """.formatted(edition, edition));
        out.append(VALUE_SHAPES);
        Set<String> keywords = new HashSet<>();
        for (DataElement e : elements) {
            if (e.element().contains("x")) {
                continue;
            }
            if (e.group().contains("x")) {
                int base = Integer.parseInt(e.group().substring(0, 2) + "00", 16);
                for (int g = base; g <= base + 0x1E; g += 2) {
                    String hex = String.format("%04X", g) + e.element();
                    element(out, e, hex, null, e.keyword().isEmpty() ? hex : e.keyword() + "-" + String.format("%04X", g));
                }
            } else {
                String hex = e.group() + e.element();
                // a keyword is used once only; any second use (none in current editions) gets hex
                String keyword = !e.keyword().isEmpty() && keywords.add(e.keyword()) ? e.keyword() : null;
                element(out, e, hex, keyword, keyword != null ? keyword : hex);
            }
        }
        return out.toString();
    }

    private static void element(StringBuilder out, DataElement e, String hex, String keyword, String shapeName) {
        String vrs = String.join(" , ", e.vrs().stream().map(v -> quote(v)).toList());
        String label = e.name().isEmpty() ? "" : "    rdfs:label " + quote(e.name()) + " ;\n";
        // both predicates carry the VR and VM, which the converter reads
        String annotations = label
            + "    dcm:vr " + vrs + " ;\n"
            + "    dcm:vm " + quote(e.vm()) + (e.retired() ? " ;\n    dcm:retired true" : "");
        out.append("dcm:").append(hex).append("\n    a rdf:Property ;\n")
            .append(e.keyword().isEmpty() ? "" : "    dcm:keyword " + quote(e.keyword()) + " ;\n")
            .append(annotations).append(" .\n");
        if (keyword != null) {
            out.append("dcm:").append(keyword).append("\n    a rdf:Property ;\n    owl:equivalentProperty dcm:")
                .append(hex).append(" ;\n").append(annotations).append(" .\n");
        }
        String predicates = keyword != null ? "dcm:" + hex + " , dcm:" + keyword : "dcm:" + hex;
        String path = keyword != null ? "[ sh:alternativePath ( dcm:" + hex + " dcm:" + keyword + " ) ]" : "dcm:" + hex;
        out.append("dcmsh:").append(shapeName).append("\n    a sh:PropertyShape ;\n")
            .append("    sh:targetSubjectsOf ").append(predicates).append(" ;\n")
            .append("    sh:path ").append(path).append(" ;\n")
            .append("    sh:maxCount 1 ;\n")
            .append(label)
            .append(valueConstraint(e, hex)).append(" .\n\n");
    }

    // the forms the value may take: one per VR, plus the forms options turn lists into
    private static String valueConstraint(DataElement e, String hex) {
        boolean single = e.vm().equals("1");
        Set<String> forms = new LinkedHashSet<>();
        for (String vr : e.vrs()) {
            String shape = valueShape(vr);
            if (vr.equals("SQ")) {
                forms.add("[ sh:node dcmsh:List-Item ]");
            } else if (BINARY.contains(vr) || single) {
                forms.add("[ sh:node dcmsh:" + shape + " ]");
            } else {
                forms.add("[ sh:node dcmsh:List-" + shape.substring("Value-".length()) + " ]");
            }
        }
        if (e.vrs().contains("SQ") || (!single && !e.vrs().stream().allMatch(BINARY::contains))) {
            // -cdt turns lists of plain values (and of -detlef's named items) into one literal
            forms.add("[ sh:datatype cdt:List ]");
        }
        if (hex.equals("30060050")) {
            // -wkt turns ContourData into a GeoSPARQL literal
            forms.add("[ sh:datatype geo:wktLiteral ]");
        }
        if (forms.size() == 1) {
            return "    " + forms.iterator().next().replaceAll("^\\[ (.*) \\]$", "$1");
        }
        return "    sh:or ( " + String.join(" ", forms) + " )";
    }

    private static String valueShape(String vr) {
        return switch (vr) {
            case "AE", "AS", "AT", "CS", "LO", "LT", "SH", "ST", "UC", "UR", "UT" -> "Value-String";
            case "UI" -> "Value-UI";
            case "PN" -> "Value-PN";
            case "DA" -> "Value-DA";
            case "DT" -> "Value-DT";
            case "TM" -> "Value-TM";
            case "DS" -> "Value-DS";
            case "IS" -> "Value-IS";
            case "FL" -> "Value-FL";
            case "FD" -> "Value-FD";
            case "SS", "US", "SL", "SV", "UV" -> "Value-Integer";
            case "UL" -> "Value-UL";
            case "OB", "OD", "OF", "OL", "OV", "OW", "UN" -> "Value-Binary";
            case "SQ" -> "Value-Item";
            default -> throw new IllegalArgumentException("unknown VR " + vr);
        };
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // What one value of each kind of VR looks like in dcm2rdf output, and the rdf:List of them a
    // multi-valued element holds. A list may hold dcm:Null for an empty value.
    private static final String VALUE_SHAPES = """
        dcmsh:Value-String a sh:NodeShape ; sh:datatype xsd:string .
        dcmsh:Value-UI a sh:NodeShape ;
            # an IRI with -oid
            sh:or ( [ sh:datatype xsd:string ] [ sh:nodeKind sh:IRI ] ) .
        dcmsh:Value-PN a sh:NodeShape ;
            # an IRI when -detlef names it
            sh:nodeKind sh:BlankNodeOrIRI ;
            sh:property [ sh:path dcm:Alphabetic ; sh:maxCount 1 ; sh:datatype xsd:string ] ;
            sh:property [ sh:path dcm:Ideographic ; sh:maxCount 1 ; sh:datatype xsd:string ] ;
            sh:property [ sh:path dcm:Phonetic ; sh:maxCount 1 ; sh:datatype xsd:string ] .
        dcmsh:Value-DA a sh:NodeShape ;
            sh:or ( [ sh:datatype xsd:date ] [ sh:datatype dcm:invalidDA ] ) .
        dcmsh:Value-DT a sh:NodeShape ;
            sh:or ( [ sh:datatype xsd:dateTime ] [ sh:datatype xsd:date ] [ sh:datatype xsd:gYearMonth ] [ sh:datatype xsd:gYear ] [ sh:datatype dcm:invalidDT ] ) .
        dcmsh:Value-TM a sh:NodeShape ;
            sh:or ( [ sh:datatype xsd:time ] [ sh:datatype dcm:invalidTM ] ) .
        dcmsh:Value-DS a sh:NodeShape ;
            sh:or ( [ sh:datatype xsd:decimal ] [ sh:datatype xsd:double ] [ sh:datatype dcm:invalidDS ] ) .
        dcmsh:Value-IS a sh:NodeShape ;
            sh:or ( [ sh:datatype xsd:integer ] [ sh:datatype dcm:invalidIS ] ) .
        dcmsh:Value-FL a sh:NodeShape ; sh:datatype xsd:float .
        dcmsh:Value-FD a sh:NodeShape ; sh:datatype xsd:double .
        dcmsh:Value-Integer a sh:NodeShape ; sh:datatype xsd:integer .
        dcmsh:Value-UL a sh:NodeShape ; sh:datatype xsd:unsignedInt .
        dcmsh:Value-Binary a sh:NodeShape ;
            # binary values are never lists: a node holding the value, or saying it was left out
            sh:nodeKind sh:BlankNode ;
            sh:property [ sh:path dcm:vr ; sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ] ;
            sh:xone (
                [ sh:property [ sh:path dcm:InlineBinary ; sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:base64Binary ] ]
                [ sh:property [ sh:path dcm:InlineBinaryOmitted ; sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:integer ] ]
                [ sh:property [ sh:path dcm:BulkDataURI ; sh:minCount 1 ; sh:maxCount 1 ] ]
                [ sh:property [ sh:path dcm:BulkDataOmitted ; sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:integer ] ]
                [ sh:property [ sh:path dcm:DataFragment ; sh:minCount 1 ; sh:maxCount 1 ] ]
            ) .
        dcmsh:Value-Item a sh:NodeShape ;
            # a sequence item: an IRI when -detlef names it
            sh:nodeKind sh:BlankNodeOrIRI .

        """ + lists() + "\n";

    private static String lists() {
        StringBuilder sb = new StringBuilder();
        for (String kind : List.of("String", "UI", "PN", "DA", "DT", "TM", "DS", "IS", "FL", "FD", "Integer", "UL", "Item")) {
            sb.append("dcmsh:List-").append(kind).append(" a sh:NodeShape ;\n")
                .append("    sh:property [ sh:path rdf:first ; sh:minCount 1 ; sh:maxCount 1 ] ;\n")
                .append("    sh:property [ sh:path ( [ sh:zeroOrMorePath rdf:rest ] rdf:first ) ;\n")
                .append("                  sh:or ( [ sh:node dcmsh:Value-").append(kind).append(" ] [ sh:class dcm:Null ] ) ] .\n");
        }
        return sb.toString();
    }
}
