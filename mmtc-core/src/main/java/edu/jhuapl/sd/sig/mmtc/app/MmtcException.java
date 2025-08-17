package edu.jhuapl.sd.sig.mmtc.app;

/**
 * Defines an application-specific exception class.
 */
public class MmtcException extends Exception {
    public MmtcException(String msg) {
        super(msg);
    }

    public MmtcException(Throwable cause) { super (cause); }

    public MmtcException(String msg, Throwable cause) {
        super(msg, cause);
    }


}
