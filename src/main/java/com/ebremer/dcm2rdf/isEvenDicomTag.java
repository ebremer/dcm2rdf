package com.ebremer.dcm2rdf;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;

public class isEvenDicomTag extends FunctionBase1 {

    @Override
    public NodeValue exec(NodeValue v) {
        Node node = v.asNode();
        if (node.isURI()) {
            String uriString = node.getURI();
            String basePart = getBasePart(uriString);
            if (!DCM.NS.equals(basePart)) {
                return NodeValue.FALSE;
            }
            String localPart = getLocalPart(uriString);
            if (localPart.matches("[0-9A-Fa-f]{8}")) {
                String firstFourDigits = localPart.substring(0, 4);
                int firstFour = Integer.parseInt(firstFourDigits, 16);
                boolean isEven = firstFour % 2 == 0;
                return NodeValue.makeBoolean(isEven);
            } else {
                return NodeValue.FALSE;
            }
        } else {
            throw new IllegalArgumentException("Argument must be a URI");
        }
    }

    private String getLocalPart(String uri) {
        int lastSlashIndex = uri.lastIndexOf('/');
        int lastHashIndex = uri.lastIndexOf('#');
        int lastColonIndex = uri.lastIndexOf(':');        
        int index = Math.max(lastSlashIndex, Math.max(lastHashIndex, lastColonIndex));
        if (index != -1) {
            return uri.substring(index + 1);
        } else {
            return uri;
        }
    }
    
    private String getBasePart(String uri) {
        int lastSlashIndex = uri.lastIndexOf('/');
        int lastHashIndex = uri.lastIndexOf('#');
        int lastColonIndex = uri.lastIndexOf(':');        
        int index = Math.max(lastSlashIndex, Math.max(lastHashIndex, lastColonIndex));
        if (index != -1) {
            return uri.substring(0, index + 1);
        } else {
            return "";
        }
    }
}
