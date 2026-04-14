package com.ebremer.dcm2rdf.libs;

import com.ebremer.dcm2rdf.ns.DCM;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.util.TagUtils;

/**
 * Shared logic for the even/odd DICOM tag group tests. A predicate matches when it is in the
 * DCM namespace and its local part is (or resolves via dictionary keyword to) an 8-digit hex
 * tag whose group number has the requested parity.
 */
abstract class DicomTagParityFunction extends FunctionBase1 {
    private final boolean wantEven;

    protected DicomTagParityFunction(boolean wantEven) {
        this.wantEven = wantEven;
    }

    @Override
    public NodeValue exec(NodeValue v) {
        Node node = v.asNode();
        if (!node.isURI()) {
            throw new IllegalArgumentException("Argument must be a URI");
        }
        String uriString = node.getURI();
        if (!DCM.NS.equals(getBasePart(uriString))) {
            return NodeValue.FALSE;
        }
        String localPart = getLocalPart(uriString);
        int tag = ElementDictionary.getStandardElementDictionary().tagForKeyword(localPart, null);
        if (tag > 0) {
            localPart = TagUtils.toHexString(tag);
        }
        if (localPart.matches("[0-9A-Fa-f]{8}")) {
            int firstFour = Integer.parseInt(localPart.substring(0, 4), 16);
            boolean isEven = firstFour % 2 == 0;
            return NodeValue.makeBoolean(isEven == wantEven);
        }
        return NodeValue.FALSE;
    }

    private String getLocalPart(String uri) {
        int index = lastSeparator(uri);
        return index != -1 ? uri.substring(index + 1) : uri;
    }

    private String getBasePart(String uri) {
        int index = lastSeparator(uri);
        return index != -1 ? uri.substring(0, index + 1) : "";
    }

    private int lastSeparator(String uri) {
        return Math.max(uri.lastIndexOf('/'), Math.max(uri.lastIndexOf('#'), uri.lastIndexOf(':')));
    }
}
