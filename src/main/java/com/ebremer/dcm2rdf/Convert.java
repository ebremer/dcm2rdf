package com.ebremer.dcm2rdf;

import com.ebremer.dcm2rdf.utils.VRFormatException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    
    private static final Pattern DATEPATTERN = Pattern.compile("^(\\d{4})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])$");
    private static final Pattern DECIMALPATTERN = Pattern.compile("^[+-]?\\d{0,15}(\\.\\d{1,15})?([eE][+-]?\\d{1,15})?$");
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "^(?:[01]\\d|2[0-3])" +                // HH: "00" to "23"
        "(?:(?:[0-5]\\d)?" +                   // MM: "00" to "59" (optional)
        "(?:[0-5]\\d|60)?" +                   // SS: "00" to "60" (optional, leap second allowed)
        "(?:\\.\\d{1,6})?)?" +                 // FFFFFF: Optional fractional part with 1-6 digits
        "\\s*$"                                // Optional trailing spaces
    );
    
    public static String removeTrailingDot(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.endsWith(".") ? input.substring(0, input.length() - 1) : input;
    }

    public static Literal toTM(String timeValue) {
        if (!timeValue.matches("^\\d{2}(?:\\d{2}(?:\\d{2}(?:\\.\\d{1,6})?)?)?$")) {
            throw new VRFormatException(String.format("Invalid time format. Expected HHMMSS.FFFFFF or subsets thereof [%s]", timeValue));
        }
        StringBuilder xsdTimeBuilder = new StringBuilder();
        xsdTimeBuilder.append(timeValue, 0, 2);
        if (timeValue.length() > 2) {
            xsdTimeBuilder.append(":").append(timeValue, 2, 4);
        } else {
            xsdTimeBuilder.append(":00");
        }
        if (timeValue.length() > 4) {
            xsdTimeBuilder.append(":").append(timeValue, 4, 6);
        } else {
            xsdTimeBuilder.append(":00");
        }
        if (timeValue.length() > 6) {
            xsdTimeBuilder.append(timeValue.substring(6));
        }
        String xsdTime = xsdTimeBuilder.toString();
        return ResourceFactory.createTypedLiteral(xsdTime, XSDDatatype.XSDtime);
    }
    
    public static Literal toDA(String datetime) throws IllegalArgumentException {
        String dicomRegex = "^(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\.(\\d{1,6}))?([+-]\\d{4})?$";
        Pattern pattern = Pattern.compile(dicomRegex);
        Matcher matcher = pattern.matcher(datetime.trim());
        if (!matcher.matches()) {
            throw new VRFormatException(String.format("Invalid DICOM DA format [%s]", datetime));
        }
        String year = matcher.group(1);
        String month = matcher.group(2) != null ? matcher.group(2) : "01";
        String day = matcher.group(3) != null ? matcher.group(3) : "01";
        String hour = matcher.group(4) != null ? matcher.group(4) : "00";
        String minute = matcher.group(5) != null ? matcher.group(5) : "00";
        String second = matcher.group(6) != null ? matcher.group(6) : "00";
        String fractionalSecond = matcher.group(8) != null ? matcher.group(8) : "";
        String offset = matcher.group(9) != null ? matcher.group(9) : "+0000";
        while (fractionalSecond.length() < 6) {
            fractionalSecond += "0";
        }
        String formattedOffset = offset.substring(0, 3) + ":" + offset.substring(3);
        String xsdDateTime = String.format("%s-%s-%sT%s:%s:%s.%s%s", year, month, day, hour, minute, second, fractionalSecond, formattedOffset);
        try {
            LocalDateTime.parse(xsdDateTime.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid DICOM DateTime conversion to XSD DateTime", e);
        }
        return ResourceFactory.createTypedLiteral(xsdDateTime, XSDDatatype.XSDdateTime);
    }
    
    public static Literal toDT(String date) {
        String pdate = date.trim();
        if (DATEPATTERN.matcher(pdate).matches()) {
            return ResourceFactory.createTypedLiteral(String.format("%s-%s-%s",pdate.subSequence(0, 4), pdate.substring(4, 6), pdate.substring(6)), XSDDatatype.XSDdate);
        }
        throw new VRFormatException("Invalid date string [YYYYMMDD] "+date);
    }

    public static Literal toDS(String input) {
        String trimmedInput = input.trim();
        if (trimmedInput.length() > 16) {
            throw new VRFormatException(String.format("Input exceeds maximum length of 16 characters [%s]", input));
        }
        if (!trimmedInput.matches("[+\\-]?[0-9]*(\\.[0-9]*)?([Ee][+\\-]?[0-9]+)?")) {
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
        if (!trimmedInput.matches("[+\\-]?[0-9]+")) {
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
