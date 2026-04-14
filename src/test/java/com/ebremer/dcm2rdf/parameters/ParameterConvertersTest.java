package com.ebremer.dcm2rdf.parameters;

import com.beust.jcommander.ParameterException;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParameterConvertersTest {

    @Test
    void logLevelAcceptsAllDocumentedValues() {
        LogLevelConverter c = new LogLevelConverter();
        assertEquals(Level.OFF, c.convert("OFF"));
        assertEquals(Level.SEVERE, c.convert("SEVERE"));
        assertEquals(Level.WARNING, c.convert("warning"));
        assertEquals(Level.INFO, c.convert("Info"));
        assertEquals(Level.FINE, c.convert("FINE"));
        assertEquals(Level.ALL, c.convert("all"));
    }

    @Test
    void logLevelRejectsUnknownValues() {
        LogLevelConverter c = new LogLevelConverter();
        assertThrows(ParameterException.class, () -> c.convert("LOUD"));
        assertThrows(ParameterException.class, () -> c.convert(""));
    }

    @Test
    void namingNormalizesCaseAndValidates() {
        NamingConverter c = new NamingConverter();
        assertEquals("SHA256", c.convert("sha256"));
        assertEquals("SHA256", c.convert("SHA256"));
        assertEquals("SOPInstanceUID", c.convert("sopinstanceuid"));
        assertThrows(ParameterException.class, () -> c.convert("md5"));
    }
}
