package edu.jhuapl.sd.sig.mmtc.automation.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.autocorrelate.config.AutocorrelateConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.trending.config.TrendingConfig;

public class AutomationAppConfig extends MmtcConfig {

    public AutomationAppConfig(AutomationAppCliConfig automationAppCliConfig) throws Exception {
        super();
    }

    public void validate() throws MmtcException {
        super.validate();

        // test construct 'child' configuration objects and make sure they're valid
        try {
            new AutocorrelateConfig().validate();
            new TrendingConfig().validate();
        } catch (Exception e) {
            throw new MmtcException(e);
        }

        // ensure that at least one automation mode is enabled
        if (! (getAutomationConfig().autocorrelate.isEnabled || getAutomationConfig().trending.isEnabled || getAutomationConfig().autocorrelateAndTrending.isEnabled)) {
            throw new MmtcException("Must enable at least one automation mode to run.");
        }
    }
}
