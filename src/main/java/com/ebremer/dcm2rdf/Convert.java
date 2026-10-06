package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.VRFormatException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 *
 * @author erich
 */
public class Convert {

    // DA: YYYYMMDD, or the pre-3.0 YYYY.MM.DD that PS3.5 still recommends accepting
    private static final Pattern DATEPATTERN = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})|(\\d{4})\\.(\\d{2})\\.(\\d{2})");
    // DT: YYYY[MM[DD[HH[MM[SS[.F{1,6}]]]]]][&ZZXX] - each part only after the one before it
    private static final Pattern DT_PATTERN = Pattern.compile(
        "(\\d{4})(?:(\\d{2})(?:(\\d{2})(?:(\\d{2})(?:(\\d{2})(?:(\\d{2})(?:\\.(\\d{1,6}))?)?)?)?)?)?(?:([+-])(\\d{2})(\\d{2}))?");
    private static final Pattern DSPATTERN = Pattern.compile("[+\\-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([Ee][+\\-]?[0-9]+)?");
    private static final Pattern ISPATTERN = Pattern.compile("[+\\-]?[0-9]+");
    private static final Pattern TMPATTERN = Pattern.compile("\\d{2}(?:\\d{2}(?:\\d{2}(?:\\.\\d{1,6})?)?)?");

    public static Literal toTM(String timeValue) {
        if (!TMPATTERN.matcher(timeValue).matches()) {
            throw new VRFormatException(String.format("Invalid time format. Expected HHMMSS.FFFFFF or subsets thereof [%s]", timeValue));
        }
        int nhours = Integer.parseInt(timeValue.substring(0, 2));
        if ((nhours < 0) || (nhours > 23)) {
            throw new NumberFormatException(String.format("Hours must be 0 - 23.  Invalid DICOM TM format [%s]", timeValue));
        }
        StringBuilder xsdTimeBuilder = new StringBuilder();
        xsdTimeBuilder.append(timeValue, 0, 2);
        if (timeValue.length() > 2) {
            int minutes = Integer.parseInt(timeValue.substring(2, 4));
            if (minutes > 59) {
                throw new VRFormatException(String.format("Minutes must be 0 - 59.  Invalid DICOM TM format [%s]", timeValue));
            }
            xsdTimeBuilder.append(":").append(timeValue, 2, 4);
        } else {
            xsdTimeBuilder.append(":00");
        }
        if (timeValue.length() > 4) {
            int seconds = Integer.parseInt(timeValue.substring(4, 6));
            // DICOM allows 60 for leap seconds
            if (seconds > 60) {
                throw new VRFormatException(String.format("Seconds must be 0 - 60.  Invalid DICOM TM format [%s]", timeValue));
            }
            xsdTimeBuilder.append(":").append(timeValue, 4, 6);
        } else {
            xsdTimeBuilder.append(":00");
        }
        if (timeValue.length() > 6) {
            xsdTimeBuilder.append(timeValue.substring(6));
        }
        return ResourceFactory.createTypedLiteral(xsdTimeBuilder.toString(), XSDDatatype.XSDtime);
    }

    /**
     * DICOM DA (YYYYMMDD, or the legacy YYYY.MM.DD) to xsd:date. The date must exist; year 0000,
     * which XSD 1.0 lacks and DICOM data uses as a placeholder, is rejected.
     *
     * @throws VRFormatException if the value is not a DA date
     */
    public static Literal toXsdDate(String date) {
        Matcher m = DATEPATTERN.matcher(date.trim());
        if (!m.matches()) {
            throw new VRFormatException("Invalid date string [YYYYMMDD] " + date);
        }
        int g = m.group(1) != null ? 1 : 4;
        String year = m.group(g), month = m.group(g + 1), day = m.group(g + 2);
        checkDate(date, year, month, day);
        return ResourceFactory.createTypedLiteral(year + "-" + month + "-" + day, XSDDatatype.XSDdate);
    }

    /**
     * DICOM DT to the XSD type of its precision, keeping the fraction and the UTC offset:
     * xsd:gYear (YYYY), xsd:gYearMonth (YYYYMM), xsd:date (YYYYMMDD), or xsd:dateTime once an
     * hour is given. xsd:dateTime needs minutes and seconds, so a time cut short is completed
     * with zeros - the only padding done. A leap second (SS = 60) has no XSD form.
     *
     * @throws VRFormatException if the value is not a DT date-time
     */
    public static Literal toXsdDT(String datetime) {
        Matcher m = DT_PATTERN.matcher(datetime.trim());
        if (!m.matches()) {
            throw new VRFormatException(String.format("Invalid DICOM DT format [%s]", datetime));
        }
        String year = m.group(1), month = m.group(2), day = m.group(3), hour = m.group(4);
        String offset = "";
        if (m.group(8) != null) {
            int hh = Integer.parseInt(m.group(9)), mm = Integer.parseInt(m.group(10));
            // XSD offsets run to 14:00
            if (hh > 14 || mm > 59 || (hh == 14 && mm != 0)) {
                throw new VRFormatException(String.format("Invalid DICOM DT offset [%s]", datetime));
            }
            offset = m.group(8) + m.group(9) + ":" + m.group(10);
        }
        if (month == null) {
            checkDate(datetime, year, "01", "01");
            return ResourceFactory.createTypedLiteral(year + offset, XSDDatatype.XSDgYear);
        }
        if (day == null) {
            checkDate(datetime, year, month, "01");
            return ResourceFactory.createTypedLiteral(year + "-" + month + offset, XSDDatatype.XSDgYearMonth);
        }
        checkDate(datetime, year, month, day);
        String date = year + "-" + month + "-" + day;
        if (hour == null) {
            return ResourceFactory.createTypedLiteral(date + offset, XSDDatatype.XSDdate);
        }
        String minute = m.group(5) != null ? m.group(5) : "00";
        String second = m.group(6) != null ? m.group(6) : "00";
        if (Integer.parseInt(hour) > 23 || Integer.parseInt(minute) > 59 || Integer.parseInt(second) > 59) {
            throw new VRFormatException(String.format("Invalid DICOM DT time [%s]", datetime));
        }
        String fraction = m.group(7) != null ? "." + m.group(7) : "";
        return ResourceFactory.createTypedLiteral(
            date + "T" + hour + ":" + minute + ":" + second + fraction + offset, XSDDatatype.XSDdateTime);
    }

    private static void checkDate(String value, String year, String month, String day) {
        try {
            if (Integer.parseInt(year) == 0) {
                throw new VRFormatException(String.format("Year 0000 is not a date [%s]", value));
            }
            LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException ex) {
            throw new VRFormatException(String.format("No such date [%s]", value));
        }
    }

    public static Literal toDS(String input) {
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
            throw new NumberFormatException(String.format("Input does not match the DICOM DS format [%s]", input));
        }
        if (trimmedInput.length() > 16) {
            throw new VRFormatException(String.format("Input exceeds maximum length of 16 characters [%s]", input));
        }
        if (!DSPATTERN.matcher(trimmedInput).matches()) {
            throw new VRFormatException(String.format("Input does not match the DICOM DS format [%s]", input));
        }
        if (trimmedInput.toLowerCase(Locale.ROOT).contains("e")) {
            return ResourceFactory.createTypedLiteral(trimmedInput, XSDDatatype.XSDdouble);
        } else {
            return ResourceFactory.createTypedLiteral(trimmedInput, XSDDatatype.XSDdecimal);
        }
    }

    public static Literal toIS(String input) {
        String trimmedInput = input.trim();
        if (trimmedInput.length() > 12) {
            throw new VRFormatException(String.format("Input exceeds maximum length of 12 characters. [%s]", input));
        }
        if (!ISPATTERN.matcher(trimmedInput).matches()) {
            throw new VRFormatException(String.format("Input does not match the DICOM IS format [%s]", input));
        }
        try {
            long value = Long.parseLong(trimmedInput);
            if (value < -2147483648L || value > 2147483647L) {
                throw new VRFormatException("Input is outside the valid DICOM IS range (-2^31 to 2^31-1).");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Input is not a valid integer.");
        }
        return ResourceFactory.createTypedLiteral(trimmedInput, XSDDatatype.XSDinteger);
    }
}
