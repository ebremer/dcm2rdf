package com.ebremer.dcm2rdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.media.DicomDirWriter;

/**
 * Generates the synthetic DICOM set in {@code src/test/resources/smoke}: one of each input
 * dcm2rdf handles, between them covering every VR, the transfer syntaxes, a multi-byte
 * character set, nested and repeating-group attributes, RT contours, DICOMDIR and nested tar
 * archives. {@link SmokeTest} converts it under every option on the JVM, CI does the same with
 * the native binary, and it is the input for regenerating {@code config/reachability-metadata.json}
 * with the native-image tracing agent. No real patient data is involved.
 * <p>
 * Regenerate with: {@code java -cp <test classpath> com.ebremer.dcm2rdf.SmokeFixtures src/test/resources/smoke}
 */
public final class SmokeFixtures {

    /** Number of RDF outputs a conversion of the set produces. */
    public static final int OUTPUTS = 10;

    /** Number of RDF outputs with -sniff, which also takes the two files without an extension. */
    public static final int OUTPUTS_SNIFFED = 12;

    private SmokeFixtures() {
    }

    public static void main(String[] args) throws IOException {
        write(Path.of(args.length > 0 ? args[0] : "src/test/resources/smoke"));
    }

    public static void write(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve("ct.dcm"), encode(ct(), UID.ExplicitVRLittleEndian));
        Files.write(dir.resolve("jp.dcm"), encode(japanese(), UID.ExplicitVRLittleEndian));
        Files.write(dir.resolve("rtstruct.dcm"), encode(rtstruct(), UID.ExplicitVRLittleEndian));
        Files.write(dir.resolve("implicit.dcm"), encode(ct("2.25.1004"), UID.ImplicitVRLittleEndian));
        Files.write(dir.resolve("bigendian.dcm"), encode(ct("2.25.1005"), UID.ExplicitVRBigEndian));
        Files.write(dir.resolve("deflated.dcm"), encode(ct("2.25.1006"), UID.DeflatedExplicitVRLittleEndian));
        Files.write(dir.resolve("legacy.dat"), encode(ct("2.25.1007"), UID.ExplicitVRLittleEndian));
        // DICOM by content only: converted with -sniff
        Files.write(dir.resolve("IM0001"), encode(ct("2.25.1011"), UID.ExplicitVRLittleEndian));
        Files.deleteIfExists(dir.resolve("DICOMDIR"));
        DicomDirWriter.createEmptyDirectory(dir.resolve("DICOMDIR").toFile(), "2.25.1008", "SMOKE", null, null);
        Files.write(dir.resolve("archive.tar"), archive());
    }

    private static byte[] encode(Attributes attrs, String tsuid) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // file meta is always explicit VR little endian; the dataset follows in tsuid
        try (DicomOutputStream dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(attrs.createFileMetaInformation(tsuid), attrs);
        }
        return baos.toByteArray();
    }

    private static Attributes base(String sopClassUID, String sopInstanceUID) {
        Attributes a = new Attributes();
        a.setString(Tag.SOPClassUID, VR.UI, sopClassUID);
        a.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        a.setString(Tag.StudyInstanceUID, VR.UI, "2.25.2001");
        a.setString(Tag.SeriesInstanceUID, VR.UI, "2.25.2002");
        a.setString(Tag.PatientID, VR.LO, "42");
        a.setString(Tag.StudyDate, VR.DA, "20240115");
        a.setString(Tag.StudyTime, VR.TM, "123045.5");
        return a;
    }

    private static Attributes code(String value) {
        Attributes item = new Attributes();
        item.setString(Tag.CodeValue, VR.SH, value);
        item.setString(Tag.CodingSchemeDesignator, VR.SH, "99SMOKE");
        return item;
    }

    private static Attributes ct() {
        return ct("2.25.1001");
    }

    private static Attributes ct(String sopInstanceUID) {
        Attributes a = base(UID.CTImageStorage, sopInstanceUID);
        a.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");
        a.setString(Tag.PatientName, VR.PN, "Müller^Jürgen");
        a.setString(Tag.Modality, VR.CS, "CT");
        a.setString(Tag.AcquisitionDateTime, VR.DT, "20240115123045.123456+0500");
        a.setString(Tag.FrameAcquisitionDateTime, VR.DT, "2024");
        a.setString(Tag.FrameReferenceDateTime, VR.DT, "202401");
        a.setString(Tag.SeriesDate, VR.DA, "2024.01.16");
        // not an OID: stays a literal with -oid
        a.setString(Tag.FrameOfReferenceUID, VR.UI, "1.2.abc");
        a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "AXIAL");
        a.setString(Tag.ImageOrientationPatient, VR.DS, "1", "0", "0", "0", "1", "0");
        a.setString(Tag.ImagePositionPatient, VR.DS, "-125.5", "-125.5", "1.25e2");
        a.setString(Tag.PixelSpacing, VR.DS, "0.5", "0.5");
        // values that do not parse, which are kept and logged rather than failing the file
        a.setString(Tag.SliceThickness, VR.DS, "1.0.0");
        a.setString(Tag.SeriesTime, VR.TM, "2561");
        a.setString(Tag.ContentDate, VR.DA, "20241315");
        a.setString(Tag.InstanceNumber, VR.IS, "1.5");
        // sequences: one item, several items, and a sequence nested in an item
        a.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("ONE"));
        Attributes first = code("A");
        Sequence modifiers = first.newSequence(Tag.ModifierCodeSequence, 2);
        modifiers.add(code("A1"));
        modifiers.add(code("A2"));
        Sequence procedures = a.newSequence(Tag.ProcedureCodeSequence, 2);
        procedures.add(first);
        procedures.add(code("B"));
        // two overlay planes: a repeating group
        for (int group : new int[] {0x60000000, 0x60020000}) {
            a.setInt(group | 0x0010, VR.US, 4);
            a.setInt(group | 0x0011, VR.US, 4);
            a.setString(group | 0x0040, VR.CS, "G");
            a.setInt(group | 0x0050, VR.SS, 1, 1);
            a.setInt(group | 0x0100, VR.US, 1);
            a.setInt(group | 0x0102, VR.US, 0);
            a.setBytes(group | 0x3000, VR.OW, new byte[2]);
        }
        // a private block holding one element of every remaining VR
        a.setString(0x00090010, VR.LO, "DCM2RDF SMOKE");
        a.setFloat(0x00091001, VR.FL, 1.5f, Float.NaN, Float.POSITIVE_INFINITY);
        a.setDouble(0x00091002, VR.FD, 2.5, Double.NEGATIVE_INFINITY);
        a.setInt(0x00091003, VR.SL, -7, 7);
        a.setInt(0x00091004, VR.SS, -3);
        a.setInt(0x00091005, VR.UL, 0xFFFFFFFF);
        a.setInt(0x00091006, VR.US, 65535);
        a.setLong(0x00091007, VR.SV, Long.MIN_VALUE);
        a.setLong(0x00091008, VR.UV, -1L);
        a.setInt(0x00091009, VR.AT, Tag.PatientID);
        a.setBytes(0x0009100A, VR.OB, new byte[] {1, 2, 3, 4});
        a.setBytes(0x0009100B, VR.OW, new byte[] {1, 2, 3, 4});
        a.setBytes(0x0009100C, VR.OF, new byte[8]);
        a.setBytes(0x0009100D, VR.OD, new byte[8]);
        a.setBytes(0x0009100E, VR.OL, new byte[4]);
        a.setBytes(0x0009100F, VR.OV, new byte[8]);
        a.setBytes(0x00091010, VR.UN, new byte[] {5, 6});
        a.setString(0x00091011, VR.UT, "unlimited text");
        a.setString(0x00091012, VR.UR, "https://example.org/smoke");
        a.setString(0x00091013, VR.UC, "a", "b");
        a.setString(0x00091014, VR.AS, "042Y");
        a.setString(0x00091015, VR.AE, "SMOKE");
        a.setString(0x00091016, VR.ST, "short text");
        a.setString(0x00091017, VR.LT, "long text");
        // pixel data last: conversion stops there
        a.setInt(Tag.Rows, VR.US, 2);
        a.setInt(Tag.Columns, VR.US, 2);
        a.setInt(Tag.BitsAllocated, VR.US, 16);
        a.setInt(Tag.BitsStored, VR.US, 12);
        a.setInt(Tag.HighBit, VR.US, 11);
        a.setInt(Tag.PixelRepresentation, VR.US, 0);
        a.setInt(Tag.SamplesPerPixel, VR.US, 1);
        a.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        a.setBytes(Tag.PixelData, VR.OW, new byte[8]);
        return a;
    }

    private static Attributes japanese() {
        Attributes a = base(UID.SecondaryCaptureImageStorage, "2.25.1002");
        a.setString(Tag.SpecificCharacterSet, VR.CS, "", "ISO 2022 IR 87");
        a.setString(Tag.PatientName, VR.PN, "Yamada^Tarou=山田^太郎=やまだ^たろう");
        a.setString(Tag.Modality, VR.CS, "OT");
        return a;
    }

    private static Attributes rtstruct() {
        Attributes a = base(UID.RTStructureSetStorage, "2.25.1003");
        a.setString(Tag.Modality, VR.CS, "RTSTRUCT");
        Attributes roi = new Attributes();
        roi.setInt(Tag.ROINumber, VR.IS, 1);
        roi.setString(Tag.ROIName, VR.LO, "BODY");
        a.newSequence(Tag.StructureSetROISequence, 1).add(roi);
        Attributes polygon = new Attributes();
        polygon.setString(Tag.ContourGeometricType, VR.CS, "CLOSED_PLANAR");
        polygon.setInt(Tag.NumberOfContourPoints, VR.IS, 4);
        polygon.setString(Tag.ContourData, VR.DS, "0", "0", "0", "10", "0", "0", "10", "10", "0", "0", "10", "0");
        Attributes point = new Attributes();
        point.setString(Tag.ContourGeometricType, VR.CS, "POINT");
        point.setInt(Tag.NumberOfContourPoints, VR.IS, 1);
        point.setString(Tag.ContourData, VR.DS, "5", "5", "0");
        Attributes roiContour = new Attributes();
        roiContour.setInt(Tag.ReferencedROINumber, VR.IS, 1);
        Sequence contours = roiContour.newSequence(Tag.ContourSequence, 2);
        contours.add(polygon);
        contours.add(point);
        a.newSequence(Tag.ROIContourSequence, 1).add(roiContour);
        return a;
    }

    private static byte[] archive() throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(inner)) {
            entry(tos, "b.dcm", encode(ct("2.25.1010"), UID.ExplicitVRLittleEndian));
        }
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(outer)) {
            TarArchiveEntry dir = new TarArchiveEntry("series/");
            dir.setModTime(0);
            tos.putArchiveEntry(dir);
            tos.closeArchiveEntry();
            entry(tos, "series/a.dcm", encode(ct("2.25.1009"), UID.ExplicitVRLittleEndian));
            entry(tos, "series/IM0002", encode(ct("2.25.1012"), UID.ExplicitVRLittleEndian));
            entry(tos, "nested.tar", inner.toByteArray());
            entry(tos, "notes.txt", "not DICOM".getBytes());
            entry(tos, "empty.dcm", new byte[0]);
        }
        return outer.toByteArray();
    }

    private static void entry(TarArchiveOutputStream tos, String name, byte[] data) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name);
        e.setModTime(0);
        e.setSize(data.length);
        tos.putArchiveEntry(e);
        tos.write(data);
        tos.closeArchiveEntry();
    }
}
