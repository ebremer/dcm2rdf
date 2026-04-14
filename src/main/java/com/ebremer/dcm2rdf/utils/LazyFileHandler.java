package com.ebremer.dcm2rdf.utils;

import java.io.IOException;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Defers creating the underlying {@link FileHandler} - and therefore the log file itself -
 * until the first record is actually published, so runs that log nothing leave no file behind.
 */
public class LazyFileHandler extends Handler {
    private final String pattern;
    private FileHandler delegate;

    public LazyFileHandler(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        if (delegate == null) {
            try {
                delegate = new FileHandler(pattern, false);
                delegate.setLevel(getLevel());
                if (getFormatter() != null) {
                    delegate.setFormatter(getFormatter());
                }
                if (getFilter() != null) {
                    delegate.setFilter(getFilter());
                }
            } catch (IOException | SecurityException ex) {
                reportError("Cannot create log file " + pattern, ex, ErrorManager.OPEN_FAILURE);
                return;
            }
        }
        delegate.publish(record);
    }

    @Override
    public synchronized void flush() {
        if (delegate != null) {
            delegate.flush();
        }
    }

    @Override
    public synchronized void close() {
        if (delegate != null) {
            delegate.close();
        }
    }
}
