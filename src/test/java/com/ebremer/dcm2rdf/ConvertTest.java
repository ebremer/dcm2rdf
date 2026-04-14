package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.VRFormatException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertTest {

    // ----- removeTrailingDot -----

    @Test
    void removeTrailingDot_stripsSingleTrailingDot() {
        assertEquals("abc", Convert.removeTrailingDot("abc."));
    }

    @Test
    void removeTrailingDot_leavesUnchangedWhenNoDot() {
        assertEquals("abc", Convert.removeTrailingDot("abc"));
    }

    @Test
    void removeTrailingDot_handlesEmptyAndNull() {
        assertEquals("", Convert.removeTrailingDot(""));
        assertNull(Convert.removeTrailingDot(null));
    }

    @Test
    void removeTrailingDot_onlyStripsOne() {
        assertEquals("abc.", Convert.removeTrailingDot("abc.."));
    }

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

    // ----- toDA (parses DICOM DT-style strings to xsd:dateTime) -----

    @Test
    void toDA_dateOnly() {
        Literal l = Convert.toDA("20240115");
        assertEquals("2024-01-15T00:00:00", l.getLexicalForm());
        assertEquals(XSDDatatype.XSDdateTime, l.getDatatype());
    }

    @Test
    void toDA_fullDateTime() {
        assertEquals("2024-01-15T12:30:45",
            Convert.toDA("20240115123045").getLexicalForm());
    }

    @Test
    void toDA_handlesZeroedPlaceholderString() {
        assertEquals("0001-01-01T00:00:00",
            Convert.toDA("0000-00-00T00:00:00").getLexicalForm());
    }

    @Test
    void toDA_promotesYearZero() {
        assertEquals("0001-01-01T00:00:00",
            Convert.toDA("00000000").getLexicalForm());
    }

    @Test
    void toDA_rejectsGarbage() {
        assertThrows(VRFormatException.class, () -> Convert.toDA("not-a-date"));
    }

    // ----- toDT (strict YYYYMMDD -> xsd:date) -----

    @Test
    void toDT_valid() {
        Literal l = Convert.toDT("20240115");
        assertEquals("2024-01-15", l.getLexicalForm());
        assertEquals(XSDDatatype.XSDdate, l.getDatatype());
    }

    @Test
    void toDT_rejectsWrongLength() {
        assertThrows(VRFormatException.class, () -> Convert.toDT("202401"));
    }

    @Test
    void toDT_rejectsInvalidMonth() {
        assertThrows(VRFormatException.class, () -> Convert.toDT("20241315"));
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

    // ----- toFL / toFD -----

    @Test
    void toFL_basic() {
        Literal l = Convert.toFL("1.5");
        assertEquals(XSDDatatype.XSDfloat, l.getDatatype());
        assertEquals(1.5f, Float.parseFloat(l.getLexicalForm()));
    }

    @Test
    void toFL_rejectsGarbage() {
        assertThrows(VRFormatException.class, () -> Convert.toFL("xyz"));
    }

    @Test
    void toFD_basic() {
        Literal l = Convert.toFD("3.14159");
        assertEquals(XSDDatatype.XSDdouble, l.getDatatype());
        assertEquals(3.14159d, Double.parseDouble(l.getLexicalForm()));
    }

    @Test
    void toFD_rejectsGarbage() {
        assertThrows(VRFormatException.class, () -> Convert.toFD("xyz"));
    }
}
