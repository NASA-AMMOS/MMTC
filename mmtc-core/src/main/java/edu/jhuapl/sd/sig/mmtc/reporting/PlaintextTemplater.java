package edu.jhuapl.sd.sig.mmtc.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class PlaintextTemplater<T> extends Templater<T> {
    private static final Logger logger = LogManager.getLogger();

    public PlaintextTemplater(T ctx, String builtInDefaultTemplateFilename) {
        super(ctx, builtInDefaultTemplateFilename);
    }
}
