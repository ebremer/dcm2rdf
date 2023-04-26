package com.ebremer.dcm2rdf;

/**
 *
 * @author erich
 */
public class Statistics {
    private static Statistics statistics = null;
    private long nfiles;
    private long bytes = 0;
    private final long start;
    
    private Statistics() {
        nfiles = 0;
        bytes = 0;
        start = System.nanoTime();
    }
    
    public synchronized void AddFile(long size, long nfiles) {
        this.nfiles = this.nfiles + nfiles;
        bytes = bytes + size;
    }
    
    public static synchronized  Statistics getStatistics() {
        if (statistics==null) {
            statistics = new Statistics();
        }
        return statistics;
    }
    
    public synchronized String getStats() {
        double delta = ((System.nanoTime()-start)/1000000000d);
        double rate = (((double) bytes)/1024d/1024d)/delta;
        return "Rate --> "+String.format("%.3f",rate)+" MB/sec for "+nfiles+" files @ "+String.format("%.3f",((double) nfiles)/delta)+" files/sec";
    }
}
