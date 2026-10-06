package com.ebremer.dcm2rdf.utils;

/**
 * A run's file and byte counts, and its rates since the run began (when this was created).
 *
 * @author erich
 */
public class Statistics {
    private long nfiles = 0;
    private long totalbytes = 0;
    private long bytesread = 0;
    private final long start;

    public Statistics() {
        start = System.nanoTime();
    }

    public synchronized void addFile(long size, long nfiles) {
        this.nfiles = this.nfiles + nfiles;
        this.totalbytes = this.totalbytes + size;
    }

    public synchronized void addBytesRead(long bytes) {
        this.bytesread = this.bytesread + bytes;
    }

    public synchronized String getStats() {
        double delta = ((System.nanoTime()-start)/1000000000d);
        return String.format("""
            Total bytes      : %,.0f MB
            Actually read    : %,.0f MB
            # of files       : %,d
            Total time       : %,.3f sec
            Data Rate        : %,.3f MB/sec
            Actual Data Rate : %,.3f MB/sec
            Fraction Read    : %,.1f percent
            File Rate        : %,.3f files/sec
            """,
            ((double) totalbytes) / 1024d / 1024d,
            ((double) bytesread) / 1024d / 1024d,
            nfiles,
            delta,
            ((double) totalbytes)/delta / 1024d / 1024d,
            ((double) bytesread)/delta / 1024d / 1024d,
            totalbytes == 0 ? 0d : 100d*((double) bytesread)/((double) totalbytes),
            ((double) nfiles)/delta
        );
    }
}
