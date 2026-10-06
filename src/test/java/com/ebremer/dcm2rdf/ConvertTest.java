package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.VRFormatException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertTest {

    // ----- toTM -----

    @Test
    void toTM_hoursOnly() {
        Literal l = Convert.toTM("12");
        assertEquals("12:00:00", l.getLexicalForm());
        assertEquals(XSDDatatype.XSDtime, l.getDatatype());
    }

    @Test
    void toTM_hhmm() {
        assertEquals("12:30:00", Convert.toTM("1230").getLexicalForm());
    }

    @Test
    void toTM_hhmmss() {
        assertEquals("12:30:45", Convert.toTM("123045").getLexicalForm());
    }

    @Test
    void toTM_withFractional() {
        assertEquals("12:30:45.5", Convert.toTM("123045.5").getLexicalForm());
    }

    @Test
    void toTM_boundaryHour23() {
        assertEquals("23:59:59", Convert.toTM("235959").getLexicalForm());
    }

    @Test
    void toTM_rejectsMalformed() {
        assertThrows(VRFormatException.class, () -> Convert.toTM("abc"));
        assertThrows(VRFormatException.class, () -> Convert.toTM("1"));
    }

    @Test
    void toTM_rejectsHoursOutOfRange() {
        assertThrows(NumberFormatException.class, () -> Convert.toTM("25"));
    }

    @Test
    void toTM_rejectsMinutesAndSecondsOutOfRange() {
        assertThrows(VRFormatException.class, () -> Convert.toTM("1260"));
        assertThrows(VRFormatException.class, () -> Convert.toTM("125961"));
    }

    @Test
    void toTM_allowsLeapSecond() {
        assertEquals("12:59:60", Convert.toTM("125960").getLexicalForm());
    }

    // ----- toXsdDate (strict YYYYMMDD -> xsd:date) -----

    @Test
    void toXsdDate_valid() {
        Literal l = Convert.toXsdDate("20240115");
        assertEquals("2024-01-15", l.getLexicalForm());
        assertEquals(XSDDatatype.XSDdate, l.getDatatype());
    }

    @Test
    void toXsdDate_rejectsWrongLength() {
        assertThrows(VRFormatException.class, () -> Convert.toXsdDate("202401"));
    }

    @Test
    void toXsdDate_rejectsInvalidMonth() {
        assertThrows(VRFormatException.class, () -> Convert.toXsdDate("20241315"));
    }

    @Test
    void toXsdDate_acceptsTheLegacyDottedForm() {
        assertEquals("2024-01-16", Convert.toXsdDate("2024.01.16").getLexicalForm());
    }

    @Test
    void toXsdDate_rejectsDatesThatDoNotExist() {
        assertThrows(VRFormatException.class, () -> Convert.toXsdDate("20230229"));
        assertThrows(VRFormatException.class, () -> Convert.toXsdDate("00000101"));
    }

    // ----- toXsdDT (DT -> the XSD type of its precision) -----

    private static void assertDT(String dicom, String lexical, XSDDatatype type) {
        Literal l = Convert.toXsdDT(dicom);
        assertEquals(lexical, l.getLexicalForm(), dicom);
        assertEquals(type, l.getDatatype(), dicom);
        assertTrue(type.isValid(lexical), lexical + " must be a valid " + type.getURI());
    }

    @Test
    void toXsdDT_keepsThePrecisionGiven() {
        assertDT("2024", "2024", XSDDatatype.XSDgYear);
        assertDT("202401", "2024-01", XSDDatatype.XSDgYearMonth);
        assertDT("20240115", "2024-01-15", XSDDatatype.XSDdate);
        // xsd:dateTime needs minutes and seconds: the only padding done
        assertDT("2024011512", "2024-01-15T12:00:00", XSDDatatype.XSDdateTime);
    }

    @Test
    void toXsdDT_keepsFractionAndOffset() {
        assertDT("20240115123045.123456+0500", "2024-01-15T12:30:45.123456+05:00", XSDDatatype.XSDdateTime);
        assertDT("20240115123045.5-0330", "2024-01-15T12:30:45.5-03:30", XSDDatatype.XSDdateTime);
        assertDT("2024+0100", "2024+01:00", XSDDatatype.XSDgYear);
    }

    @Test
    void toXsdDT_rejectsWhatXsdCannotHold() {
        assertThrows(VRFormatException.class, () -> Convert.toXsdDT("20241315"));
        assertThrows(VRFormatException.class, () -> Convert.toXsdDT("2024011525"));
        assertThrows(VRFormatException.class, () -> Convert.toXsdDT("20240115235960"));
        assertThrows(VRFormatException.class, () -> Convert.toXsdDT("20240115+1500"));
        assertThrows(VRFormatException.class, () -> Convert.toXsdDT("2024.5"));
        assertThrows(VRFormatException.class, () -> Convert.toXsdDT("0000"));
    }

    // ----- toDS -----

    @Test
    void toDS_decimal() {
        Literal l = Convert.toDS("123.45");
        assertEquals("123.45", l.getLexicalForm());
        assertEquals(XSDDatatype.XSDdecimal, l.getDatatype());
    }

    @Test
    void toDS_signedDecimal() {
        assertEquals("-0.5", Convert.toDS("-0.5").getLexicalForm());
        assertEquals("+1", Convert.toDS("+1").getLexicalForm());
    }

    @Test
    void toDS_scientificIsDouble() {
        Literal l = Convert.toDS("1.23e10");
        assertEquals(XSDDatatype.XSDdouble, l.getDatatype());
        assertEquals("1.23e10", l.getLexicalForm());
    }

    @Test
    void toDS_trimsWhitespace() {
        assertEquals("42", Convert.toDS("  42  ").getLexicalForm());
    }

    @Test
    void toDS_rejectsEmpty() {
        assertThrows(NumberFormatException.class, () -> Convert.toDS(""));
        assertThrows(NumberFormatException.class, () -> Convert.toDS("   "));
    }

    @Test
    void toDS_rejectsDigitlessInput() {
        assertThrows(VRFormatException.class, () -> Convert.toDS("."));
        assertThrows(VRFormatException.class, () -> Convert.toDS("+"));
    }

    @Test
    void toDS_rejectsOverlong() {
        assertThrows(VRFormatException.class,
            () -> Convert.toDS("12345678901234567"));
    }

    @Test
    void toDS_rejectsNonNumeric() {
        assertThrows(VRFormatException.class, () -> Convert.toDS("abc"));
    }

    // ----- toIS -----

    @Test
    void toIS_positive() {
        Literal l = Convert.toIS("123");
        assertEquals("123", l.getLexicalForm());
        assertEquals(XSDDatatype.XSDinteger, l.getDatatype());
    }

    @Test
    void toIS_negative() {
        assertEquals("-456", Convert.toIS("-456").getLexicalForm());
    }

    @Test
    void toIS_int32Bounds() {
        assertEquals("2147483647", Convert.toIS("2147483647").getLexicalForm());
        assertEquals("-2147483648", Convert.toIS("-2147483648").getLexicalForm());
    }

    @Test
    void toIS_rejectsOutOfRange() {
        assertThrows(VRFormatException.class, () -> Convert.toIS("2147483648"));
        assertThrows(VRFormatException.class, () -> Convert.toIS("-2147483649"));
    }

    @Test
    void toIS_rejectsOverlong() {
        assertThrows(VRFormatException.class, () -> Convert.toIS("1234567890123"));
    }

    @Test
    void toIS_rejectsDecimal() {
        assertThrows(VRFormatException.class, () -> Convert.toIS("1.5"));
    }
}
