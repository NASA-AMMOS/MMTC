package edu.jhuapl.sd.sig.mmtc.app;

public class TelemetryQualityException extends MmtcException {
    public TelemetryQualityException(String msg) {
        super(msg);
    }

    public TelemetryQualityException(Throwable cause) {
        super(cause);
    }

    public TelemetryQualityException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
