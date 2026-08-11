package edu.jhuapl.sd.sig.mmtc.app;

public class NoTelemetryFoundException extends MmtcException {
    public NoTelemetryFoundException(String msg) {
        super(msg);
    }

    public NoTelemetryFoundException(Throwable cause) {
        super(cause);
    }

    public NoTelemetryFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
