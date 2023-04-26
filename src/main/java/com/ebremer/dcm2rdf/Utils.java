package com.ebremer.dcm2rdf;


public class Utils {

    public static long GetTotal() {
        Runtime rt = Runtime.getRuntime();
        long left = rt.totalMemory()/(1024L*1024L*1024L);
        return left;
    }
    
    public static long GetMax() {
        Runtime rt = Runtime.getRuntime();
        long left = rt.maxMemory()/(1024L*1024L*1024L);
        return left;
    }
    
    public static long GetFree() {
        Runtime rt = Runtime.getRuntime();
        long left = rt.freeMemory()/(1024L*1024L*1024L);
        return left;
    }
}