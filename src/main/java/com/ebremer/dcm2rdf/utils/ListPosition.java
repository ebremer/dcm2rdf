package com.ebremer.dcm2rdf.utils;

import java.util.Iterator;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.Symbol;
import org.apache.jena.vocabulary.RDF;

public class ListPosition extends FunctionBase1 {
    private Graph graph;
    
    @Override
    public void build(String uri, ExprList args, Context context) { 
        Dataset o = (Dataset) context.get(Symbol.create("http://jena.apache.org/ARQ/system#dataset"));     
        o.listModelNames().forEachRemaining(r->System.out.println(r));
        graph = o.getDefaultModel().getGraph();
    }
    
    @Override
    public NodeValue exec(Binding binding, ExprList args, String uri, FunctionEnv env) {
        return super.exec(binding, args, uri, env);
    }

    @Override
    public NodeValue exec(NodeValue elementNV) {
        //Node listNode = listNV.asNode();
        Node targetElement = elementNV.asNode();
        Integer position = findElementPosition(targetElement);
        if (position == null) {
            throw new ExprEvalException("Element not found in list");
        }
        return NodeValue.makeInteger(position);
    }
    
    private Integer findElementPosition(Node targetElement) {
        int position = 0;
        Node currentNode = targetElement;
        while (true) {
            Iterator<Triple> restIter = graph.find(Node.ANY, RDF.rest.asNode(), currentNode);
            if (!restIter.hasNext()) {
                return position;
            }            
            currentNode = restIter.next().getSubject();           
            position++;
        }
    }
}