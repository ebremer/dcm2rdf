package com.ebremer.dcm2rdf.utils;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.cdt.CDTFactory;
import org.apache.jena.cdt.CDTValue;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;
import org.apache.jena.sparql.function.FunctionEnv;

public class rdf2cdtList extends FunctionBase1 {
    private Model m;
    
    @Override
    public NodeValue exec(Binding binding, ExprList args, String uri, FunctionEnv env) {
        m = ModelFactory.createModelForGraph(env.getActiveGraph());
        return super.exec(binding, args, uri, env);
    }

    @Override
    public NodeValue exec(NodeValue v) {
        if (!v.isIRI() && !v.isBlank()) {
            throw new ExprEvalException("Input is not a resource: " + v);
        }
        Resource resource = m.asRDFNode(v.asNode()).asResource();
        RDFList list = resource.as(RDFList.class);        
        List<CDTValue> nlist = new ArrayList<>();
        list.iterator().forEach(rn->{
            nlist.add(CDTFactory.createValue(rn.asNode()));     
        });
        CDTValue cdtlist = CDTFactory.createValue(nlist);
        return NodeValue.makeNode(cdtlist.asNode());
    }
}