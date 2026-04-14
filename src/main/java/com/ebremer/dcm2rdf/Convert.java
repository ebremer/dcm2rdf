package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.VRFormatException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger logger = Logger.getLogger(Convert.class.getName());

    private static final Pattern DATEPATTERN = Pattern.compile("^(\\d{4})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])$");
    private static final Pattern DSPATTERN = Pattern.compile("[+\\-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([Ee][+\\-]?[0-9]+)?");
    private static final Pattern ISPATTERN = Pattern.compile("[+\\-]?[0-9]+");
    private static final Pattern TMPATTERN = Pattern.compile("\\d{2}(?:\\d{2}(?:\\d{2}(?:\\.\\d{1,6})?)?)?");
    private static final Pattern DTPATTERN = Pattern.compile("^(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\.(\\d{1,6}))?([+-]\\d{4})?$");

    public static String removeTrailingDot(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.endsWith(".") ? input.substring(0, input.length() - 1) : input;
    }

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
     * Parses a DICOM DA or DT style value (YYYY[MM[DD[HH[MM[SS[.FFFFFF]]]]]][+/-ZZZZ]) into an
     * xsd:dateTime literal truncated to whole seconds. Fractional seconds and UTC offsets are
     * accepted on input but not carried into the output. Zeroed date components are promoted
     * to 01 so the result is a valid xsd:dateTime.
     */
    public static Literal toXsdDateTime(String datetime) {
        Matcher matcher = DTPATTERN.matcher(datetime.trim());
        if (!matcher.matches()) {
            logger.log(Level.WARNING, String.format("Invalid DICOM DA format [%s]", datetime));
            if (datetime.equals("0000-00-00T00:00:00")) {
                logger.log(Level.WARNING, "Not a valid xsd:datetime string --> 0000-00-00T00:00:00");
                return ResourceFactory.createTypedLiteral("0001-01-01T00:00:00", XSDDatatype.XSDdateTime);
            }
            throw new VRFormatException(String.format("Invalid DICOM DA format [%s]", datetime));
        }
        String year = matcher.group(1);
        year = year.equals("0000")?"0001":year;
        String month = matcher.group(2) != null ? matcher.group(2) : "01";
        month = month.equals("00")?"01":month;
        String day = matcher.group(3) != null ? matcher.group(3) : "01";
        day = day.equals("00")?"01":day;
        String hour = matcher.group(4) != null ? matcher.group(4) : "00";
        String minute = matcher.group(5) != null ? matcher.group(5) : "00";
        String second = matcher.group(6) != null ? matcher.group(6) : "00";
        String xsdDateTime = String.format("%s-%s-%sT%s:%s:%s", year, month, day, hour, minute, second);
        try {
            LocalDateTime.parse(xsdDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new VRFormatException(String.format("Invalid DICOM DateTime conversion to XSD DateTime for %s", datetime));
        }
        return ResourceFactory.createTypedLiteral(xsdDateTime, XSDDatatype.XSDdateTime);
    }

    /**
     * @deprecated the name is misleading - this method parses full DT-style values, not just DA.
     * Use {@link #toXsdDateTime}.
     */
    @Deprecated
    public static Literal toDA(String datetime) {
        return toXsdDateTime(datetime);
    }

    /**
     * Strict DICOM DA (YYYYMMDD) to xsd:date. Not currently used by the writer, which emits
     * xsd:dateTime for both DA and DT values via {@link #toXsdDateTime}.
     */
    public static Literal toXsdDate(String date) {
        String pdate = date.trim();
        if (DATEPATTERN.matcher(pdate).matches()) {
            return ResourceFactory.createTypedLiteral(String.format("%s-%s-%s",pdate.subSequence(0, 4), pdate.substring(4, 6), pdate.substring(6)), XSDDatatype.XSDdate);
        }
        throw new VRFormatException("Invalid date string [YYYYMMDD] " + date);
    }

    /**
     * @deprecated the name is misleading - this method parses date-only DA values, not DT.
     * Use {@link #toXsdDate}.
     */
    @Deprecated
    public static Literal toDT(String date) {
        return toXsdDate(date);
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
        if (trimmedInput.toLowerCase().contains("e")) {
            return ResourceFactory.createTypedLiteral(trimmedInput, XSDDatatype.XSDdouble);
        } else {
            return ResourceFactory.createTypedLiteral(trimmedInput, XSDDatatype.XSDdecimal);
        }
    }

    public static Literal toFL(String src) {
        try {
            return ResourceFactory.createTypedLiteral((Float.valueOf(src.trim())).toString(), XSDDatatype.XSDfloat);
        } catch (NumberFormatException ex) {
            throw new VRFormatException("Invalid Float String : "+src);
        }
    }

    public static Literal toFD(String src) {
        try {
            return ResourceFactory.createTypedLiteral((Double.valueOf(src.trim())).toString(), XSDDatatype.XSDdouble);
        } catch (NumberFormatException ex) {
            throw new VRFormatException("Invalid Floating Point Double : "+src);
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
