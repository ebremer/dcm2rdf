package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.VRFormatException;
import com.ebremer.dcm2rdf.ns.DCM;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongFunction;
import java.util.ArrayList;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.PersonName;
import org.dcm4che3.data.PersonName.Group;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import static org.dcm4che3.data.VR.AE;
import static org.dcm4che3.data.VR.AS;
import static org.dcm4che3.data.VR.AT;
import static org.dcm4che3.data.VR.CS;
import static org.dcm4che3.data.VR.DA;
import static org.dcm4che3.data.VR.DS;
import static org.dcm4che3.data.VR.DT;
import static org.dcm4che3.data.VR.FD;
import static org.dcm4che3.data.VR.FL;
import static org.dcm4che3.data.VR.IS;
import static org.dcm4che3.data.VR.LO;
import static org.dcm4che3.data.VR.LT;
import static org.dcm4che3.data.VR.OB;
import static org.dcm4che3.data.VR.OD;
import static org.dcm4che3.data.VR.OF;
import static org.dcm4che3.data.VR.OL;
import static org.dcm4che3.data.VR.OV;
import static org.dcm4che3.data.VR.OW;
import static org.dcm4che3.data.VR.PN;
import static org.dcm4che3.data.VR.SH;
import static org.dcm4che3.data.VR.SL;
import static org.dcm4che3.data.VR.SQ;
import static org.dcm4che3.data.VR.SS;
import static org.dcm4che3.data.VR.ST;
import static org.dcm4che3.data.VR.SV;
import static org.dcm4che3.data.VR.TM;
import static org.dcm4che3.data.VR.UC;
import static org.dcm4che3.data.VR.UI;
import static org.dcm4che3.data.VR.UL;
import static org.dcm4che3.data.VR.UN;
import static org.dcm4che3.data.VR.UR;
import static org.dcm4che3.data.VR.US;
import static org.dcm4che3.data.VR.UT;
import static org.dcm4che3.data.VR.UV;
import org.dcm4che3.data.Value;
import org.dcm4che3.io.DicomInputHandler;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;

/**
 * Allows conversion of DICOM files into RDF format.
 */

public class RDFWriter implements DicomInputHandler {
    private static final Logger logger = java.util.logging.Logger.getLogger(dcm2rdf.class.getName());
    private static final int DOUBLE_MAX_BITS = 53;
    private final Deque<Boolean> hasItems = new ArrayDeque<>();
    private String replaceBulkDataURI;
    private final Model m;
    private final Stack<Resource> stack = new Stack<>();
    private final Stack<ArrayType> arrays = new Stack<>();
    private final Property Value = ResourceFactory.createProperty(DCM.NS, "Value");
    private final Property DataFragment = ResourceFactory.createProperty(DCM.NS, "DataFragment");
    private final Property pvr = ResourceFactory.createProperty(DCM.NS, "vr");
    private record ArrayType(Property name, ArrayList<RDFNode> array) {};
    private final Resource root;
    private final Path file;
    private boolean validSOP = true;
    
    public RDFWriter(Path file, Resource root) {
        this.file = file;
        this.root = root;
        this.m = root.getModel();
        this.stack.push(root);                
    }
    
    public RDFWriter(Resource root) {
        this.file = null;
        this.root = root;
        this.m = root.getModel();
        this.stack.push(root);                
    }
    
    public String getReplaceBulkDataURI() {
        return replaceBulkDataURI;
    }

    public void setReplaceBulkDataURI(String replaceBulkDataURI) {
        this.replaceBulkDataURI = replaceBulkDataURI;
    }

    public void write(Attributes attrs) {
        stack.push(m.createResource());
        writeAttributes(attrs);
        stack.pop();
    }

    public void writeAttributes(Attributes attrs) {
        final SpecificCharacterSet cs = attrs.getSpecificCharacterSet();
        try {
            attrs.accept((Attributes attrs1, int tag, VR vr, Object value) -> {                
                writeAttribute(tag, vr, value, cs, attrs1);
                return true;
            }, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeAttribute(int tag, VR vr, Object value, SpecificCharacterSet cs, Attributes attrs) {        
        if (TagUtils.isGroupLength(tag))
            return;        
        if (value instanceof Value value1)
            writeValue(value1, attrs.bigEndian(), false);
        else
            writeValue(vr, value, attrs.bigEndian(), attrs.getSpecificCharacterSet(vr), true, false);
        throw new Error("ACK!!!");
    }

    private void writeValue(Value value, boolean bigEndian, boolean single) {
        if (value.isEmpty())
            return;
        switch (value) {
            case Sequence sequence -> {
                arrays.push(new ArrayType(Value, new ArrayList<>()));
                for (Attributes item : sequence) {
                    write(item);
                }
                ArrayType at = arrays.pop();
                stack.pop().addProperty(at.name(), m.createList(at.array().iterator()));
            }
            case Fragments fragments -> {
                arrays.push(new ArrayType(DataFragment, new ArrayList<>()));
                Fragments frags = fragments;
                for (Object frag : frags) {
                    if (frag instanceof Value && ((Value) frag).isEmpty()) {
                        arrays.peek().array().add(m.createResource().addProperty(RDF.type, DCM.Null));
                    } else {
                        if (frag instanceof BulkData bulkData)
                            writeBulkData(bulkData);
                        else {
                            writeInlineBinary(frags.vr(), (byte[]) frag, bigEndian, true);
                        }
                    }
                }
                ArrayType at = arrays.pop();
                stack.pop().addProperty(at.name(), m.createList(at.array().iterator()));
            }
            case BulkData bulkData -> writeBulkData(bulkData);
            default -> throw new Error("ACK!!!");
        }
    }

    @Override
    public void readValue(DicomInputStream dis, Attributes attrs) throws IOException {
        int tag = dis.tag();
        VR vr = dis.vr();
        long len = dis.unsignedLength();
        boolean single = false;
        if (TagUtils.isGroupLength(tag)) {
            dis.readValue(dis, attrs);
        } else if (dis.isExcludeBulkData()) {
            dis.readValue(dis, attrs);
// skip annotation data.  Too bulky for the moment
        } else if (TagUtils.toHexString(tag).equals("00660016")) {
            dis.readValue(dis, attrs);
        } else {
            Resource bnode = m.createResource();    
            stack.peek().addProperty(m.createProperty(DCM.NS, TagUtils.toHexString(tag)), bnode);
            stack.push(bnode);
            stack.peek().addLiteral(pvr, vr.name());
            if (vr == VR.SQ || len == -1) {
                hasItems.addLast(false);
                dis.readValue(dis, attrs);
                if (hasItems.removeLast()) {
                    ArrayType at = arrays.pop();
                    stack.peek().addProperty(at.name(), m.createList(at.array().iterator()));
                }
            } else if (len > 0) {
                if (dis.isIncludeBulkDataURI()) {
                    writeBulkData(dis.createBulkData(dis));
                } else {
                    byte[] b = dis.readValue();
                    if (tag == Tag.TransferSyntaxUID || tag == Tag.SpecificCharacterSet || tag == Tag.PixelRepresentation || TagUtils.isPrivateCreator(tag))
                        attrs.setBytes(tag, vr, b);
                    writeValue(vr, b, dis.bigEndian(), attrs.getSpecificCharacterSet(vr), false, single);
                 }
            } else {
                //System.out.println("NO VALUE : "+TagUtils.toHexString(tag)+"  "+vr.name());
            }
            stack.pop();
        }        
    }
    
    public void dump() {
        m.setNsPrefix("dcm", DCM.NS);
        m.setNsPrefix("xsd", XSD.NS);
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
    }

    private void writeValue(VR vr, Object val, boolean bigEndian, SpecificCharacterSet cs, boolean preserve, boolean single) {        
        switch (vr) {
            case AE, AS, AT, CS, DA, DS, DT, IS, LO, LT, PN, SH, ST, TM, UC, UI, UR, UT -> writeStringValues(vr, val, bigEndian, cs, single);
            case FL -> writeFloatValues(vr, val, bigEndian, single);
            case FD -> writeDoubleValues(vr, val, bigEndian, single);
            case SL, SS, US -> writeIntValues(vr, val, bigEndian, single);
            case SV -> writeLongValues(Long::toString, vr, val, bigEndian);
            case UV -> writeLongValues(Long::toUnsignedString, vr, val, bigEndian);
            case UL -> writeUIntValues(vr, val, bigEndian, single);
            case OB, OD, OF, OL, OV, OW, UN -> writeInlineBinary(vr, (byte[]) val, bigEndian, preserve);
            case SQ -> {
                assert true;
            }
        }
    }

    private void writeStringValues(VR vr, Object val, boolean bigEndian, SpecificCharacterSet cs, boolean single) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        Object o = vr.toStrings(val, bigEndian, cs);
        String[] ss = (o instanceof String[]) ? (String[]) o : new String[]{ (String) o };        
        for (String s : ss) {
            if (s == null ) {
                arrays.peek().array().add(m.createResource().addProperty(RDF.type, DCM.Null));
            } else {
                try {
                    switch (vr) {
                        case DA -> arrays.peek().array().add(Convert.toDA(s));
                        case DS -> arrays.peek().array().add(Convert.toDS(s));
                        case IS -> arrays.peek().array().add(Convert.toIS(s));
                        case PN -> writePersonName(s);
                        case TM -> arrays.peek().array().add(Convert.toTM(s));
                        default -> arrays.peek().array().add(m.createTypedLiteral(s));
                    }
                } catch (VRFormatException err) {
                    logger.log(Level.WARNING, "VRFormatException {0} -> {1}", new Object[] {err.getMessage(), root});
                    arrays.peek().array().add(ResourceFactory.createTypedLiteral(s));
                    root.addLiteral(DCM.invalidSOPInstance, true);
                    validSOP = false;
                } catch (NumberFormatException err) {
                    logger.log(Level.WARNING, "NumberFormatException {0} -> {1}", new Object[] {err.getMessage(), root});
                    arrays.peek().array().add(ResourceFactory.createTypedLiteral(s));
                    root.addLiteral(DCM.invalidSOPInstance, true);
                    validSOP = false;                    
                }
            }
        }
        ArrayType at = arrays.pop();
        if (single) {
            stack.peek().addProperty(at.name(), at.array().iterator().next());
        } else {
            stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
        }
    }

    private void writeFloatValues(VR vr, Object val, boolean bigEndian, boolean single) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            float d = vr.toFloat(val, bigEndian, i, 0);
            if (Float.isNaN(d)) {
                logger.log(Level.INFO, "encode {0} NaN as null", new Object[] {vr, file});
                arrays.peek().array().add(m.createResource().addProperty(RDF.type, DCM.Null));
            } else {
                if (d == Float.POSITIVE_INFINITY) {
                    d = Float.MAX_VALUE;
                    logger.log(Level.WARNING, "encode {0} Infinity as {1}", new Object[] {vr, d, file});
                } else if (d == Float.NEGATIVE_INFINITY) {
                    d = -Float.MAX_VALUE;
                    logger.log(Level.WARNING, "encode {0} -Infinity as {1}", new Object[] {vr, d, file});
                }
                arrays.peek().array().add(m.createTypedLiteral(d,XSDDatatype.XSDfloat));
            }
        }
        ArrayType at = arrays.pop();
        if (single) {
            stack.peek().addProperty(at.name(), at.array().iterator().next());
        } else {
            stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
        }
    }    
    
    private void writeDoubleValues(VR vr, Object val, boolean bigEndian, boolean single) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            double d = vr.toDouble(val, bigEndian, i, 0);
            if (Double.isNaN(d)) {
                logger.log(Level.WARNING, "encode {0} Infinity as {1}", new Object[] {vr, d, file});
                arrays.peek().array().add(m.createResource().addProperty(RDF.type, DCM.Null));
            } else {
                if (d == Double.POSITIVE_INFINITY) {
                    d = Double.MAX_VALUE;
                    logger.info(String.format("encode %s Infinity as %s", vr, d));
                } else if (d == Double.NEGATIVE_INFINITY) {
                    d = -Double.MAX_VALUE;
                    logger.log(Level.WARNING, "encode {0} -Infinity as {1}", new Object[] {vr, d, file});
                }
                arrays.peek().array().add(m.createTypedLiteral(d,XSDDatatype.XSDdouble));
            }
        }
        ArrayType at = arrays.pop();
        if (single) {
            stack.peek().addProperty(at.name(), at.array().iterator().next());
        } else {
            stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
        }
    }

    private void writeIntValues(VR vr, Object val, boolean bigEndian, boolean single) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            arrays.peek().array().add(m.createTypedLiteral(vr.toInt(val, bigEndian, i, 0),XSDDatatype.XSDinteger)); 
        }
        ArrayType at = arrays.pop();
        if (single) {
            stack.peek().addProperty(at.name(), at.array().iterator().next());
        } else {
            stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
        }
    }

    private void writeUIntValues(VR vr, Object val, boolean bigEndian, boolean single) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            long num = vr.toInt(val, bigEndian, i, 0) & 0xffffffffL;
            arrays.peek().array().add(m.createTypedLiteral(num,XSDDatatype.XSDunsignedInt));
        }
        ArrayType at = arrays.pop();
        if (single) {
            stack.peek().addProperty(at.name(), at.array().iterator().next());
        } else {
            stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
        }
    }

    private void writeLongValues(LongFunction<String> toString, VR vr, Object val, boolean bigEndian) {
        boolean asString = true;
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            long l = vr.toLong(val, bigEndian, i, 0);
            if (asString || (l < 0 ? (vr == VR.UV || (-l >> DOUBLE_MAX_BITS) > 0) : (l >> DOUBLE_MAX_BITS) > 0)) {
                throw new Error("ACK!!!");
            } else {
                arrays.peek().array().add(m.createTypedLiteral(l,XSDDatatype.XSDlong));
            }
        }
    }

    private void writePersonName(String s) {
        PersonName pn = new PersonName(s, true);
        stack.push(m.createResource());
        writePNGroup("Alphabetic", pn, PersonName.Group.Alphabetic);
        writePNGroup("Ideographic", pn, PersonName.Group.Ideographic);
        writePNGroup("Phonetic", pn, PersonName.Group.Phonetic);
        arrays.peek().array().add(stack.pop());
    }

    private void writePNGroup(String name, PersonName pn, Group group) {
        if (pn.contains(group)) {
            Property dt = m.createProperty(DCM.NS,name);
            stack.peek().addProperty(dt, pn.toString(group, true));
        }            
    }

    private void writeInlineBinary(VR vr, byte[] b, boolean bigEndian, boolean preserve) {
        if (bigEndian) {
            b = vr.toggleEndian(b, preserve);
        }       
        stack.peek().addProperty(DCM.InlineBinary, m.createTypedLiteral(java.util.Base64.getEncoder().encodeToString(b), XSDDatatype.XSDbase64Binary));
    }

    private void writeBulkData(BulkData blkdata) {
        if (replaceBulkDataURI != null) {
            stack.peek().addProperty(DCM.BulkDataURI, replaceBulkDataURI);
        } else {
            stack.peek().addProperty(DCM.BulkDataURI, stack.peek().getModel().createResource(blkdata.getURI()));
        }
    }

    @Override
    public void readValue(DicomInputStream dis, Sequence seq) throws IOException {
        if (!hasItems.getLast()) {
            arrays.push(new ArrayType(Value, new ArrayList<>()));
            hasItems.removeLast();
            hasItems.addLast(true);
        }
        stack.push(m.createResource());
        arrays.peek().array().add(stack.peek());
        dis.readValue(dis, seq);
        stack.pop();
    }

    @Override
    public void readValue(DicomInputStream dis, Fragments frags) throws IOException {
        int len = dis.length();
        if (dis.isExcludeBulkData()) {
            dis.skipFully(len);
            return;
        }
        if (!hasItems.getLast()) {
            arrays.push(new ArrayType(DataFragment, new ArrayList<>()));
            hasItems.removeLast();
            hasItems.add(true);
        }
        if (len == 0)
            arrays.peek().array().add(m.createResource().addProperty(RDF.type, DCM.Null));
        else {
            stack.push(m.createResource());
            if (dis.isIncludeBulkDataURI()) {
                writeBulkData(dis.createBulkData(dis));
            } else {
                writeInlineBinary(frags.vr(), dis.readValue(), 
                dis.bigEndian(), false);
            }
            stack.pop();
            throw new Error("ACK!!!");
        }
    }

    @Override
    public void startDataset(DicomInputStream dis) throws IOException {}

    @Override
    public void endDataset(DicomInputStream dis) throws IOException {
        if (!validSOP) {
            logger.log(Level.WARNING, "Invalid SOP -> {0}", file);
        }
    }
}
