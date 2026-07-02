package com.ebremer.dcm2rdf.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class LazyFileHandlerTest {

    @Test
    void createsNoFileWhenNothingIsLogged(@TempDir Path dir) {
        Path log = dir.resolve("run.log.ttl");
        LazyFileHandler h = new LazyFileHandler(log.toString().replace('\\', '/'));
        h.setFormatter(new RDFFormatter());
        h.close();
        assertFalse(Files.exists(log));
    }

    @Test
    void createsFileOnFirstRecord(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("run.log.ttl");
        LazyFileHandler h = new LazyFileHandler(log.toString().replace('\\', '/'));
        h.setFormatter(new RDFFormatter());
        h.publish(new LogRecord(Level.SEVERE, "boom"));
        h.close();
        assertTrue(Files.exists(log));
        assertTrue(Files.readString(log).contains("boom"));
    }
}
