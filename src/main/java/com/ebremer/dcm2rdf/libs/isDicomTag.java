package com.ebremer.dcm2rdf.libs;

import com.ebremer.dcm2rdf.ns.DCM;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;

public class isDicomTag extends FunctionBase1 {
    
    @Override
    public NodeValue exec(NodeValue v) {        
        Node node = v.asNode();
        if (node.isURI()) {
            if (node.getURI().startsWith(DCM.NS)) {
                return NodeValue.TRUE;
            }
        }
        return NodeValue.FALSE;
    }
}
