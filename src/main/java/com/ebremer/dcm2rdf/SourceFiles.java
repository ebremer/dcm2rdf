package com.ebremer.dcm2rdf;

import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOM;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DICOMDIR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.DIRECTORY;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.TAR;
import static com.ebremer.dcm2rdf.DirectoryProcessor.FileType.UNKNOWN;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Recognising the sources the command line converts: by name, or by the DICOM file marker.
 */
final class SourceFiles {

    private SourceFiles() {}

    // the "DICM" marker of a DICOM file follows a 128-byte preamble
    static final int PREAMBLE_LENGTH = 132;

    static boolean isDicomPreamble(byte[] head) {
        return head.length >= PREAMBLE_LENGTH
            && head[128] == 'D' && head[129] == 'I' && head[130] == 'C' && head[131] == 'M';
    }

    // a file that can't be read is not taken for DICOM; it is counted as another file
    static boolean hasDicomPreamble(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return isDicomPreamble(in.readNBytes(PREAMBLE_LENGTH));
        } catch (IOException ex) {
            return false;
        }
    }

    static DirectoryProcessor.FileType getFileType(String file) {
        String r = file.toLowerCase(Locale.ROOT);
        if ( r.endsWith(".dcm") || r.endsWith(".dat") ) {
            return DICOM;
        } else if (r.endsWith(".tar")) {
            return TAR;
        }
        int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        if (file.substring(sep + 1).equals("DICOMDIR")) {
            return DICOMDIR;
        }
        return UNKNOWN;
    }

    static DirectoryProcessor.FileType getFileType(Path file) {
        if (file.toFile().isDirectory()) {
            return DIRECTORY;
        } else {
            String r = file.toString().toLowerCase(Locale.ROOT);
            if ( r.endsWith(".dcm") || r.endsWith(".dat") ) {
                return DICOM;
            } else if (file.getFileName().toString().equals("DICOMDIR")) {
                return DICOMDIR;
            } else if (r.endsWith(".tar")) {
                return TAR;
            }
            return UNKNOWN;
        }
    }

    static String stripExtension(String name) {
        // as case-blind as getFileType: IMG.DCM becomes IMG
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dcm") || lower.endsWith(".dat")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
}
