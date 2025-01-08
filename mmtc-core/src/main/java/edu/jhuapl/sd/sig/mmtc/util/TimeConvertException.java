package edu.jhuapl.sd.sig.mmtc.util;

/**
 * Defines an exception class for use with the TimeConvert class.
 */
public class TimeConvertException extends Exception {
    public TimeConvertException(String msg) {
        super(msg);
    }
    public TimeConvertException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
