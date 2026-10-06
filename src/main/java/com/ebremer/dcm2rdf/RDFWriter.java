package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.ns.DCM;
import com.ebremer.dcm2rdf.parameters.Parameters;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongFunction;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.ElementDictionary;
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
import org.dcm4che3.io.DicomInputHandler;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;

/**
 * Allows conversion of DICOM files into RDF format.
 * <p>
 * Internal: applications embedding dcm2rdf should use {@link Dcm2RdfBuilder}, whose API is the
 * supported one. This class may change without notice.
 */

public class RDFWriter implements DicomInputHandler {
    private static final Logger logger = Logger.getLogger(RDFWriter.class.getName());
    private final Deque<Boolean> hasItems = new ArrayDeque<>();
    private final Model m;
    private final Deque<Resource> stack = new ArrayDeque<>();
    private final Deque<ArrayType> arrays = new ArrayDeque<>();
    private final Property Value = ResourceFactory.createProperty(DCM.NS, "Value");
    private final Property DataFragment = ResourceFactory.createProperty(DCM.NS, "DataFragment");
    private final Property pvr = ResourceFactory.createProperty(DCM.NS, "vr");
    private record ArrayType(Property name, ArrayList<RDFNode> array) {};
    private final Resource root;
    // logical name of the source (a path, a URI, or <archive>#<entry>); only used in log messages
    private final String file;
    private final Path src;
    // values that did not parse for their VR
    private int invalidValues;
    // bytes of the excluded bulk data element being skipped, when it has fragments
    private long omittedBytes;
    private final Parameters params;

    public RDFWriter(Path src, String file, Resource root, Parameters params) {
        this.src = src;
        this.file = file;
        this.root = root;
        this.m = root.getModel();
        this.stack.push(root);
        this.params = params;
    }

    public RDFWriter(Path src, Path file, Resource root, Parameters params) {
        this(src, String.valueOf(file), root, params);
    }

    public RDFWriter(Path file, Resource root, Parameters params) {
        // no separate source path in this form; use the file so log messages stay meaningful
        this(file, String.valueOf(file), root, params);
    }

    // Overlay (60xx), curve (50xx) and variable pixel data (7Fxx) groups repeat, and the dictionary
    // gives an element the same keyword in every group (60003000 and 60023000 are both OverlayData)
    static boolean isRepeatingGroup(int tag) {
        int group = tag & 0xFFE00000;
        return group == 0x50000000 || group == 0x60000000 || group == 0x7F000000;
    }

    @Override
    public void readValue(DicomInputStream dis, Attributes attrs) throws IOException {
        int tag = dis.tag();
        VR vr = dis.vr();
        long len = dis.unsignedLength();
        if (TagUtils.isGroupLength(tag)) {
            dis.readValue(dis, attrs);
        } else if (dis.isExcludeBulkData()) {
            // Bulk data (overlay planes, waveforms, encapsulated documents, ...) is not converted.
            // Record that it was there, and how many bytes it held, rather than drop the attribute.
            Resource bnode = m.createResource();
            stack.peek().addProperty(predicate(tag, attrs), bnode);
            bnode.addLiteral(pvr, vr.name());
            omittedBytes = 0;
            dis.readValue(dis, attrs);
            bnode.addLiteral(DCM.BulkDataOmitted, byteCount(len == -1 ? omittedBytes : len));
        } else {
            Resource bnode = m.createResource();
            stack.peek().addProperty(predicate(tag, attrs), bnode);
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
                    writeValue(vr, b, dis.bigEndian(), attrs.getSpecificCharacterSet(vr), false);
                 }
            }
            stack.pop();
        }
    }

    private Property predicate(int tag, Attributes attrs) {
        if (params.keywords) {
            String privateCreator = attrs.getPrivateCreator(tag);
            String keyword = (privateCreator == null && !isRepeatingGroup(tag)) ? ElementDictionary.keywordOf(tag, null) : null;
            // Private, unknown (dictionary returns ""), and private-creator tags keep the hex
            // form - an empty keyword would collapse them all onto the bare namespace URI.
            // So do repeating-group tags, whose shared keyword would merge their groups.
            if (keyword != null && !keyword.isEmpty() && !keyword.equals("PrivateCreatorID")) {
                return m.createProperty(DCM.NS, keyword);
            }
        }
        return m.createProperty(DCM.NS, TagUtils.toHexString(tag));
    }

    private Literal byteCount(long n) {
        return m.createTypedLiteral(String.valueOf(n), XSDDatatype.XSDinteger);
    }

    private void writeValue(VR vr, Object val, boolean bigEndian, SpecificCharacterSet cs, boolean preserve) {
        switch (vr) {
            case AE, AS, AT, CS, DA, DS, DT, IS, LO, LT, PN, SH, ST, TM, UC, UI, UR, UT -> writeStringValues(vr, val, bigEndian, cs);
            case FL -> writeFloatValues(vr, val, bigEndian);
            case FD -> writeDoubleValues(vr, val, bigEndian);
            case SL, SS, US -> writeIntValues(vr, val, bigEndian);
            case SV -> writeLongValues(Long::toString, vr, val, bigEndian);
            case UV -> writeLongValues(Long::toUnsignedString, vr, val, bigEndian);
            case UL -> writeUIntValues(vr, val, bigEndian);
            case OB, OD, OF, OL, OV, OW, UN -> writeInlineBinary(vr, (byte[]) val, bigEndian, preserve);
            case SQ -> {
                // items arrive through readValue(DicomInputStream, Sequence)
            }
        }
    }

    private void writeStringValues(VR vr, Object val, boolean bigEndian, SpecificCharacterSet cs) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        Object o = vr.toStrings(val, bigEndian, cs);
        String[] ss = (o instanceof String[]) ? (String[]) o : new String[]{ (String) o };        
        for (String s : ss) {
            if (s == null ) {
                arrays.peek().array().add(m.createResource().addProperty(RDF.type, DCM.Null));
            } else if (vr == PN) {
                writePersonName(s);
            } else {
                arrays.peek().array().add(toLiteral(vr, s));
            }
        }
        ArrayType at = arrays.pop();
        stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
    }

    // A value that doesn't parse for its VR keeps its text, typed dcm:invalid<VR>, and the SOP
    // instance is flagged dcm:invalidSOPInstance
    private Literal toLiteral(VR vr, String s) {
        try {
            return switch (vr) {
                case DA -> Convert.toXsdDate(s);
                case DT -> Convert.toXsdDT(s);
                case DS -> Convert.toDS(s);
                case IS -> Convert.toIS(s);
                case TM -> Convert.toTM(s);
                default -> m.createTypedLiteral(s);
            };
        } catch (IllegalArgumentException err) {
            // VRFormatException and NumberFormatException alike. Each value is only logged at FINE;
            // endDataset reports the file once, as a large run can hold millions of them.
            logger.log(Level.FINE, "Invalid {0} value \"{1}\" : {2} -> {3}", new Object[] {vr, s, err.getMessage(), file});
            if (invalidValues++ == 0) {
                root.addLiteral(DCM.invalidSOPInstance, true);
            }
            return m.createTypedLiteral(s, DCM.invalid(vr));
        }
    }

    private void writeFloatValues(VR vr, Object val, boolean bigEndian) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            float d = vr.toFloat(val, bigEndian, i, 0);
            arrays.peek().array().add(Float.isFinite(d)
                ? m.createTypedLiteral(d, XSDDatatype.XSDfloat)
                : m.createTypedLiteral(nonFinite(d), XSDDatatype.XSDfloat));
        }
        ArrayType at = arrays.pop();
        stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
    }

    private void writeDoubleValues(VR vr, Object val, boolean bigEndian) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            double d = vr.toDouble(val, bigEndian, i, 0);
            arrays.peek().array().add(Double.isFinite(d)
                ? m.createTypedLiteral(d, XSDDatatype.XSDdouble)
                : m.createTypedLiteral(nonFinite(d), XSDDatatype.XSDdouble));
        }
        ArrayType at = arrays.pop();
        stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
    }

    // xsd:float and xsd:double have lexical forms of their own for these
    private static String nonFinite(double d) {
        return Double.isNaN(d) ? "NaN" : d > 0 ? "INF" : "-INF";
    }

    private void writeIntValues(VR vr, Object val, boolean bigEndian) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            arrays.peek().array().add(m.createTypedLiteral(vr.toInt(val, bigEndian, i, 0),XSDDatatype.XSDinteger));
        }
        ArrayType at = arrays.pop();
        stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
    }

    private void writeUIntValues(VR vr, Object val, boolean bigEndian) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            long num = vr.toInt(val, bigEndian, i, 0) & 0xffffffffL;
            arrays.peek().array().add(m.createTypedLiteral(num,XSDDatatype.XSDunsignedInt));
        }
        ArrayType at = arrays.pop();
        stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
    }

    private void writeLongValues(LongFunction<String> toString, VR vr, Object val, boolean bigEndian) {
        arrays.push(new ArrayType(Value, new ArrayList<>()));
        int vm = vr.vmOf(val);
        for (int i = 0; i < vm; i++) {
            long l = vr.toLong(val, bigEndian, i, 0);
            arrays.peek().array().add(m.createTypedLiteral(toString.apply(l), XSDDatatype.XSDinteger));
        }
        ArrayType at = arrays.pop();
        stack.peek().addProperty(DCM.Value, stack.peek().getModel().createList(at.array().iterator()));
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
        if (!params.includeinlinebinary) {
            // an empty InlineBinary would claim the value is empty; say it was left out, and its size
            stack.peek().addLiteral(DCM.InlineBinaryOmitted, byteCount(b.length));
            return;
        }
        if (bigEndian) {
            b = vr.toggleEndian(b, preserve);
        }
        stack.peek().addProperty(DCM.InlineBinary, m.createTypedLiteral(java.util.Base64.getEncoder().encodeToString(b), XSDDatatype.XSDbase64Binary));
    }

    private void writeBulkData(BulkData blkdata) {
        stack.peek().addProperty(DCM.BulkDataURI, m.createResource(blkdata.getURI()));
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
            omittedBytes += len;
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
            Resource item = m.createResource();
            arrays.peek().array().add(item);
            stack.push(item);
            if (dis.isIncludeBulkDataURI()) {
                writeBulkData(dis.createBulkData(dis));
            } else {
                writeInlineBinary(frags.vr(), dis.readValue(), dis.bigEndian(), false);
            }
            stack.pop();
        }
    }

    @Override
    public void startDataset(DicomInputStream dis) throws IOException {}

    @Override
    public void endDataset(DicomInputStream dis) throws IOException {
        if (invalidValues > 0) {
            logger.log(Level.WARNING, "{0} invalid value(s), typed dcm:invalid<VR> -> {1}", new Object[] {invalidValues, file});
        }
    }
}
