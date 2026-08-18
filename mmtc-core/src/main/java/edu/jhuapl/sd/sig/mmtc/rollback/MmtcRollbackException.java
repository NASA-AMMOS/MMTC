package edu.jhuapl.sd.sig.mmtc.rollback;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

public class MmtcRollbackException extends MmtcException {
    public MmtcRollbackException(String msg) {
        super(msg);
    }
    public MmtcRollbackException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
