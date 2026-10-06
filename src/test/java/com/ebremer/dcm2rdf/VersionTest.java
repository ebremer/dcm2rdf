package com.ebremer.dcm2rdf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    @Test
    void versionIsKnownWithoutAJarManifest() {
        // the test classpath holds target/classes, with no manifest: the version comes from the
        // filtered version.properties, as it does in the native image
        assertNotEquals("unknown", Dcm2RdfCli.VERSION);
        assertTrue(Dcm2RdfCli.VERSION.matches("\\d+\\.\\d+.*"), Dcm2RdfCli.VERSION);
    }
}
