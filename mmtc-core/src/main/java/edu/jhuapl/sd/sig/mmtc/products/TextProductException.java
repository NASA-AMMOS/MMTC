package edu.jhuapl.sd.sig.mmtc.products;

/**
 * Defines an exception class for use with the classes in the Time Correlation products package.
 */
public class TextProductException extends Exception {
    public TextProductException(String msg) {
        super(msg);
    }
    public TextProductException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
