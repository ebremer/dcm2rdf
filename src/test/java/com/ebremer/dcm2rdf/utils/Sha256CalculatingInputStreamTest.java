package com.ebremer.dcm2rdf.utils;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Sha256CalculatingInputStreamTest {

    private static byte[] data(int n) {
        byte[] b = new byte[n];
        new Random(42).nextBytes(b);
        return b;
    }

    private static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Test
    void hashMatchesWhenFullyRead() throws Exception {
        byte[] b = data(100_000);
        Sha256CalculatingInputStream s = new Sha256CalculatingInputStream(new ByteArrayInputStream(b));
        s.readAllBytes();
        assertEquals(sha256(b), s.getSha256Hash());
    }

    @Test
    void hashMatchesWhenPartOfTheStreamIsSkipped() throws Exception {
        // dcm4che skips excluded bulk data via skip(); those bytes must still be digested
        byte[] b = data(100_000);
        Sha256CalculatingInputStream s = new Sha256CalculatingInputStream(new ByteArrayInputStream(b));
        assertEquals(1000, s.readNBytes(1000).length);
        assertEquals(50_000, s.skip(50_000));
        s.readAllBytes();
        assertEquals(sha256(b), s.getSha256Hash());
    }

    @Test
    void singleByteReadsAreDigested() throws Exception {
        byte[] b = data(257);
        Sha256CalculatingInputStream s = new Sha256CalculatingInputStream(new ByteArrayInputStream(b));
        while (s.read() != -1) { }
        assertEquals(sha256(b), s.getSha256Hash());
    }

    @Test
    void skipPastEndReturnsActualCountAndHashStillMatches() throws Exception {
        byte[] b = data(10);
        Sha256CalculatingInputStream s = new Sha256CalculatingInputStream(new ByteArrayInputStream(b));
        assertEquals(10, s.skip(1000));
        assertEquals(sha256(b), s.getSha256Hash());
    }

    @Test
    void markResetIsDisabled() throws Exception {
        Sha256CalculatingInputStream s = new Sha256CalculatingInputStream(new ByteArrayInputStream(data(10)));
        assertFalse(s.markSupported());
        assertThrows(java.io.IOException.class, s::reset);
    }
}
