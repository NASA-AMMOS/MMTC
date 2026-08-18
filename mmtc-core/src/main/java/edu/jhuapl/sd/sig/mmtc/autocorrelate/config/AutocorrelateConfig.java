package edu.jhuapl.sd.sig.mmtc.autocorrelate.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;

public class AutocorrelateConfig extends MmtcConfigWithTlmSource {

    public AutocorrelateConfig() throws Exception {
        super();
    }

    @Override
    public void validate() throws MmtcException {
        super.validate();

        if ((! isAutocorrelatePeriodicTriggerEnabled()) && (! isAutocorrelateScetErrorTriggerEnabled())) {
            throw new MmtcException("Must enabled at least one autocorrelate trigger");
        }

        if (isAutocorrelatePeriodicTriggerEnabled()) {
            if (! containsKey("autocorrelate.trigger.periodic.periodHours")) {
                throw new MmtcException("If the periodic trigger is enabled, the period duration must be specified");
            }

            if (getAutocorrelatePeriodicTriggerPeriodHours() <= 0.0) {
                throw new MmtcException("If the periodic trigger is enabled, a period duration of more than 0 hours must be specified");
            }
        }

        if (isAutocorrelateScetErrorTriggerEnabled()) {
            if (! containsKey("autocorrelate.trigger.scetError.threshold")) {
                throw new MmtcException("If SCET Error trigger is enabled, an error threshold must be specified");
            }

            if (getAutocorrelateScetErrorTriggerThreshold() <= 0.0) {
                throw new MmtcException("If SCET Error trigger is enabled, an error threshold above 0 must be specified");
            }
        }
    }

    public boolean isTestModeOwltEnabled() {
        return getBoolean("autocorrelate.testmode.enabled", false);
    }

    public double getTestModeOwltSec() {
        return getDouble("autocorrelate.testmode.owltSec", 0.0);
    }
}
