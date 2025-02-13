package com.ebremer.dcm2rdf.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author erich bremer
 */

public class PROVO {

/**
 *  PROV-O: The PROV Ontology
 *  <p>
 *	See <a href="https://www.w3.org/TR/prov-o/">PROV-O: The PROV Ontology</a>.
 *  <p>
 *  <a href="http://www.w3.org/ns/prov#>Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://www.w3.org/ns/prov#";

    public static final Resource Activity = m.createResource(NS+"Activity");
    public static final Resource ActivityInfluence = m.createResource(NS+"ActivityInfluence");
    public static final Resource Agent = m.createResource(NS+"Agent");
    public static final Resource AgentInfluence = m.createResource(NS+"AgentInfluence");
    public static final Resource Association = m.createResource(NS+"Association");
    public static final Resource Attribution = m.createResource(NS+"Attribution");
    public static final Resource Bundle = m.createResource(NS+"Bundle");
    public static final Resource Collection = m.createResource(NS+"Collection");
    public static final Resource Communication = m.createResource(NS+"Communication");
    public static final Resource Delegation = m.createResource(NS+"Delegation");
    public static final Resource Derivation = m.createResource(NS+"Derivation");
    public static final Resource EmptyCollection = m.createResource(NS+"EmptyCollection");
    public static final Resource End = m.createResource(NS+"End");
    public static final Resource Entity = m.createResource(NS+"Entity");
    public static final Resource EntityInfluence = m.createResource(NS+"EntityInfluence");
    public static final Resource Generation = m.createResource(NS+"Generation");
    public static final Resource Influence = m.createResource(NS+"Influence");
    public static final Resource InstantaneousEvent = m.createResource(NS+"InstantaneousEvent");
    public static final Resource Invalidation = m.createResource(NS+"Invalidation");
    public static final Resource Location = m.createResource(NS+"Location");
    public static final Resource Organization = m.createResource(NS+"Organization");
    public static final Resource Person = m.createResource(NS+"Person");
    public static final Resource Plan = m.createResource(NS+"Plan");
    public static final Resource PrimarySource = m.createResource(NS+"PrimarySource");
    public static final Resource Quotation = m.createResource(NS+"Quotation");
    public static final Resource Revision = m.createResource(NS+"Revision");
    public static final Resource Role = m.createResource(NS+"Role");
    public static final Resource SoftwareAgent = m.createResource(NS+"SoftwareAgent");
    public static final Resource Start = m.createResource(NS+"Start");
    public static final Resource Thing = m.createResource(NS+"Thing");
    public static final Resource Usage = m.createResource(NS+"Usage");
    public static final Property actedOnBehalfOf = m.createProperty(NS+"actedOnBehalfOf");
    public static final Property activity = m.createProperty(NS+"activity");
    public static final Property agent = m.createProperty(NS+"agent");
    public static final Property alternateOf = m.createProperty(NS+"alternateOf");
    public static final Property atLocation = m.createProperty(NS+"atLocation");
    public static final Property entity = m.createProperty(NS+"entity");
    public static final Property generated = m.createProperty(NS+"generated");
    public static final Property hadActivity = m.createProperty(NS+"hadActivity");
    public static final Property hadGeneration = m.createProperty(NS+"hadGeneration");
    public static final Property hadMember = m.createProperty(NS+"hadMember");
    public static final Property hadPlan = m.createProperty(NS+"hadPlan");
    public static final Property hadPrimarySource = m.createProperty(NS+"hadPrimarySource");
    public static final Property hadRole = m.createProperty(NS+"hadRole");
    public static final Property hadUsage = m.createProperty(NS+"hadUsage");
    public static final Property influenced = m.createProperty(NS+"influenced");
    public static final Property influencer = m.createProperty(NS+"influencer");
    public static final Property invalidated = m.createProperty(NS+"invalidated");
    public static final Property qualifiedAssociation = m.createProperty(NS+"qualifiedAssociation");
    public static final Property qualifiedAttribution = m.createProperty(NS+"qualifiedAttribution");
    public static final Property qualifiedCommunication = m.createProperty(NS+"qualifiedCommunication");
    public static final Property qualifiedDelegation = m.createProperty(NS+"qualifiedDelegation");
    public static final Property qualifiedDerivation = m.createProperty(NS+"qualifiedDerivation");
    public static final Property qualifiedEnd = m.createProperty(NS+"qualifiedEnd");
    public static final Property qualifiedGeneration = m.createProperty(NS+"qualifiedGeneration");
    public static final Property qualifiedInfluence = m.createProperty(NS+"qualifiedInfluence");
    public static final Property qualifiedInvalidation = m.createProperty(NS+"qualifiedInvalidation");
    public static final Property qualifiedPrimarySource = m.createProperty(NS+"qualifiedPrimarySource");
    public static final Property qualifiedQuotation = m.createProperty(NS+"qualifiedQuotation");
    public static final Property qualifiedRevision = m.createProperty(NS+"qualifiedRevision");
    public static final Property qualifiedStart = m.createProperty(NS+"qualifiedStart");
    public static final Property qualifiedUsage = m.createProperty(NS+"qualifiedUsage");
    public static final Property specializationOf = m.createProperty(NS+"specializationOf");
    public static final Property used = m.createProperty(NS+"used");
    public static final Property wasAssociatedWith = m.createProperty(NS+"wasAssociatedWith");
    public static final Property wasAttributedTo = m.createProperty(NS+"wasAttributedTo");
    public static final Property wasDerivedFrom = m.createProperty(NS+"wasDerivedFrom");
    public static final Property wasEndedBy = m.createProperty(NS+"wasEndedBy");
    public static final Property wasGeneratedBy = m.createProperty(NS+"wasGeneratedBy");
    public static final Property wasInfluencedBy = m.createProperty(NS+"wasInfluencedBy");
    public static final Property wasInformedBy = m.createProperty(NS+"wasInformedBy");
    public static final Property wasInvalidatedBy = m.createProperty(NS+"wasInvalidatedBy");
    public static final Property wasQuotedFrom = m.createProperty(NS+"wasQuotedFrom");
    public static final Property wasRevisionOf = m.createProperty(NS+"wasRevisionOf");
    public static final Property wasStartedBy = m.createProperty(NS+"wasStartedBy");
}
