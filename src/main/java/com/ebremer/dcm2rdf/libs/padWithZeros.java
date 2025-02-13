package com.ebremer.dcm2rdf.libs;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;

public class padWithZeros extends FunctionBase1 {
    
    @Override
    public NodeValue exec(NodeValue v) {        
        Node node = v.asNode();
        if (node.isLiteral()) {
            String numericString = node.getLiteralLexicalForm();
            try {
                String padded = padWithZeros(numericString);
                return NodeValue.makeString(padded);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Input must be a valid numeric string", e);
            }
        } else {
            throw new IllegalArgumentException("Argument must be a Literal");
        }
    }
    
    private static String padWithZeros(String numericString) {
        if (numericString == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return String.format("%08d", Long.valueOf(numericString));
    }
}
