package com.ebremer.dcm2rdf;

import java.util.concurrent.atomic.AtomicLong;

public class FileCounter {    
    private final AtomicLong directoryCount = new AtomicLong(0);
    private final AtomicLong dicomFileCount = new AtomicLong(0);
    private final AtomicLong otherFileCount = new AtomicLong(0);
    private final AtomicLong tarFileCount = new AtomicLong(0);
   
    private final AtomicLong tarDirectoryCount = new AtomicLong(0);
    private final AtomicLong tarDicomFileCount = new AtomicLong(0);
    private final AtomicLong tarOtherFileCount = new AtomicLong(0); 
    private final AtomicLong tarTarFileCount = new AtomicLong(0);
    
    private final AtomicLong zeroLengthFileCount = new AtomicLong(0);
    private final AtomicLong failedConversionFileCount = new AtomicLong(0); 

    public void incrementOtherFileCount() {
        otherFileCount.incrementAndGet();
    }

    public void incrementDirectoryCount() {
        directoryCount.incrementAndGet();
    }

    public void incrementDicomFileCount() {
        dicomFileCount.incrementAndGet();
    }

    public void incrementZeroLengthFileCount() {
        zeroLengthFileCount.incrementAndGet();
    }
    
    public void incrementFailedConversionFileCount() {
        failedConversionFileCount.incrementAndGet();
    }

    public void incrementTarFileCount() {
        tarFileCount.incrementAndGet();
    }

    public long getTarFileCount() {
        return tarFileCount.get();
    }
    
    public long getOtherFileCount() {
        return otherFileCount.get();
    }

    public long getDirectoryCount() {
        return directoryCount.get();
    }

    public long getDicomFileCount() {
        return dicomFileCount.get();
    }
    
    public long getZeroFileCount() {
        return zeroLengthFileCount.get();
    }
    
    public long getFailedConversionFileCount() {
        return failedConversionFileCount.get();
    }

    public void incrementTarDirectoryCount() {
        tarDirectoryCount.incrementAndGet();
    }

    public void incrementTarDicomFileCount() {
        tarDicomFileCount.incrementAndGet();
    }

    public void incrementTarOtherFileCount() {
        tarOtherFileCount.incrementAndGet();
    }
    
    public void incrementTarTarFileCount() {
        tarTarFileCount.incrementAndGet();
    }

    public long getTarDirectoryCount() {
        return tarDirectoryCount.get();
    }

    public long getTarDicomFileCount() {
        return tarDicomFileCount.get();
    }

    public long getTarOtherFileCount() {
        return tarOtherFileCount.get();
    }
    
    public long getTarTarFileCount() {
        return tarTarFileCount.get();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
            ================================            
            Directories            : %d            
            DICOM files            : %d
            Other files            : %d
            Tar files              : %d            
            ================================
            """,            
            getDirectoryCount(),                        
            getDicomFileCount(),
            getOtherFileCount(),
            getTarFileCount()
        ));
        if (getTarFileCount()>0) {
            sb.append(String.format(
                """     
                Tar Directories        : %d
                Tar DICOM files        : %d
                Tar Other files        : %d
                Tar Tar files          : %d
                ================================
                """,
                getTarDirectoryCount(),                        
                getTarDicomFileCount(),
                getTarOtherFileCount(),
                getTarTarFileCount()        
            ));
        }
        sb.append(String.format(
            """
            Zero Length files      : %d
            Failed Conversions     : %d
            Successful Conversions : %d
            ================================
            """,
            getZeroFileCount(),
            getFailedConversionFileCount(),
            getDicomFileCount()+getTarFileCount()-getZeroFileCount()-getFailedConversionFileCount()
        ));
        return sb.toString();
    }
}
