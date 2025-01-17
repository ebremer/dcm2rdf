package com.ebremer.dcm2rdf.utils;

/**
 *
 * @author erich
 */
public class Statistics {
    private static Statistics statistics = null;
    private long nfiles = 0;
    private long totalbytes = 0;
    private long bytesread = 0;
    private final long start;
    
    private Statistics() {
        start = System.nanoTime();
    }
    
    public synchronized void AddFile(long size, long nfiles) {
        this.nfiles = this.nfiles + nfiles;
        this.totalbytes = this.totalbytes + size;
    }
    
    public synchronized void AddActuallyRead(long bytes) {
        this.bytesread = this.bytesread + bytes;
    }
    
    public static synchronized  Statistics getStatistics() {
        if (statistics==null) {
            statistics = new Statistics();
        }
        return statistics;
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
            100d*((double) bytesread)/((double) totalbytes),
            ((double) nfiles)/delta
        );
    }
}
