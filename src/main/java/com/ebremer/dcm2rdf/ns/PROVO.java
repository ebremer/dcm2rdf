package com.ebremer.dcm2rdf.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

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
 *  <a href="http://www.w3.org/ns/prov#">Base URI and namespace</a>.
 */
    public static final String NS = "http://www.w3.org/ns/prov#";

    public static final Resource Activity = ResourceFactory.createResource(NS+"Activity");
    public static final Resource ActivityInfluence = ResourceFactory.createResource(NS+"ActivityInfluence");
    public static final Resource Agent = ResourceFactory.createResource(NS+"Agent");
    public static final Resource AgentInfluence = ResourceFactory.createResource(NS+"AgentInfluence");
    public static final Resource Association = ResourceFactory.createResource(NS+"Association");
    public static final Resource Attribution = ResourceFactory.createResource(NS+"Attribution");
    public static final Resource Bundle = ResourceFactory.createResource(NS+"Bundle");
    public static final Resource Collection = ResourceFactory.createResource(NS+"Collection");
    public static final Resource Communication = ResourceFactory.createResource(NS+"Communication");
    public static final Resource Delegation = ResourceFactory.createResource(NS+"Delegation");
    public static final Resource Derivation = ResourceFactory.createResource(NS+"Derivation");
    public static final Resource EmptyCollection = ResourceFactory.createResource(NS+"EmptyCollection");
    public static final Resource End = ResourceFactory.createResource(NS+"End");
    public static final Resource Entity = ResourceFactory.createResource(NS+"Entity");
    public static final Resource EntityInfluence = ResourceFactory.createResource(NS+"EntityInfluence");
    public static final Resource Generation = ResourceFactory.createResource(NS+"Generation");
    public static final Resource Influence = ResourceFactory.createResource(NS+"Influence");
    public static final Resource InstantaneousEvent = ResourceFactory.createResource(NS+"InstantaneousEvent");
    public static final Resource Invalidation = ResourceFactory.createResource(NS+"Invalidation");
    public static final Resource Location = ResourceFactory.createResource(NS+"Location");
    public static final Resource Organization = ResourceFactory.createResource(NS+"Organization");
    public static final Resource Person = ResourceFactory.createResource(NS+"Person");
    public static final Resource Plan = ResourceFactory.createResource(NS+"Plan");
    public static final Resource PrimarySource = ResourceFactory.createResource(NS+"PrimarySource");
    public static final Resource Quotation = ResourceFactory.createResource(NS+"Quotation");
    public static final Resource Revision = ResourceFactory.createResource(NS+"Revision");
    public static final Resource Role = ResourceFactory.createResource(NS+"Role");
    public static final Resource SoftwareAgent = ResourceFactory.createResource(NS+"SoftwareAgent");
    public static final Resource Start = ResourceFactory.createResource(NS+"Start");
    public static final Resource Thing = ResourceFactory.createResource(NS+"Thing");
    public static final Resource Usage = ResourceFactory.createResource(NS+"Usage");
    public static final Property actedOnBehalfOf = ResourceFactory.createProperty(NS+"actedOnBehalfOf");
    public static final Property activity = ResourceFactory.createProperty(NS+"activity");
    public static final Property agent = ResourceFactory.createProperty(NS+"agent");
    public static final Property alternateOf = ResourceFactory.createProperty(NS+"alternateOf");
    public static final Property atLocation = ResourceFactory.createProperty(NS+"atLocation");
    public static final Property entity = ResourceFactory.createProperty(NS+"entity");
    public static final Property generated = ResourceFactory.createProperty(NS+"generated");
    public static final Property hadActivity = ResourceFactory.createProperty(NS+"hadActivity");
    public static final Property hadGeneration = ResourceFactory.createProperty(NS+"hadGeneration");
    public static final Property hadMember = ResourceFactory.createProperty(NS+"hadMember");
    public static final Property hadPlan = ResourceFactory.createProperty(NS+"hadPlan");
    public static final Property hadPrimarySource = ResourceFactory.createProperty(NS+"hadPrimarySource");
    public static final Property hadRole = ResourceFactory.createProperty(NS+"hadRole");
    public static final Property hadUsage = ResourceFactory.createProperty(NS+"hadUsage");
    public static final Property influenced = ResourceFactory.createProperty(NS+"influenced");
    public static final Property influencer = ResourceFactory.createProperty(NS+"influencer");
    public static final Property invalidated = ResourceFactory.createProperty(NS+"invalidated");
    public static final Property qualifiedAssociation = ResourceFactory.createProperty(NS+"qualifiedAssociation");
    public static final Property qualifiedAttribution = ResourceFactory.createProperty(NS+"qualifiedAttribution");
    public static final Property qualifiedCommunication = ResourceFactory.createProperty(NS+"qualifiedCommunication");
    public static final Property qualifiedDelegation = ResourceFactory.createProperty(NS+"qualifiedDelegation");
    public static final Property qualifiedDerivation = ResourceFactory.createProperty(NS+"qualifiedDerivation");
    public static final Property qualifiedEnd = ResourceFactory.createProperty(NS+"qualifiedEnd");
    public static final Property qualifiedGeneration = ResourceFactory.createProperty(NS+"qualifiedGeneration");
    public static final Property qualifiedInfluence = ResourceFactory.createProperty(NS+"qualifiedInfluence");
    public static final Property qualifiedInvalidation = ResourceFactory.createProperty(NS+"qualifiedInvalidation");
    public static final Property qualifiedPrimarySource = ResourceFactory.createProperty(NS+"qualifiedPrimarySource");
    public static final Property qualifiedQuotation = ResourceFactory.createProperty(NS+"qualifiedQuotation");
    public static final Property qualifiedRevision = ResourceFactory.createProperty(NS+"qualifiedRevision");
    public static final Property qualifiedStart = ResourceFactory.createProperty(NS+"qualifiedStart");
    public static final Property qualifiedUsage = ResourceFactory.createProperty(NS+"qualifiedUsage");
    public static final Property specializationOf = ResourceFactory.createProperty(NS+"specializationOf");
    public static final Property used = ResourceFactory.createProperty(NS+"used");
    public static final Property wasAssociatedWith = ResourceFactory.createProperty(NS+"wasAssociatedWith");
    public static final Property wasAttributedTo = ResourceFactory.createProperty(NS+"wasAttributedTo");
    public static final Property wasDerivedFrom = ResourceFactory.createProperty(NS+"wasDerivedFrom");
    public static final Property wasEndedBy = ResourceFactory.createProperty(NS+"wasEndedBy");
    public static final Property wasGeneratedBy = ResourceFactory.createProperty(NS+"wasGeneratedBy");
    public static final Property wasInfluencedBy = ResourceFactory.createProperty(NS+"wasInfluencedBy");
    public static final Property wasInformedBy = ResourceFactory.createProperty(NS+"wasInformedBy");
    public static final Property wasInvalidatedBy = ResourceFactory.createProperty(NS+"wasInvalidatedBy");
    public static final Property wasQuotedFrom = ResourceFactory.createProperty(NS+"wasQuotedFrom");
    public static final Property wasRevisionOf = ResourceFactory.createProperty(NS+"wasRevisionOf");
    public static final Property wasStartedBy = ResourceFactory.createProperty(NS+"wasStartedBy");
}
