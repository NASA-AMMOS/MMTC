package edu.jhuapl.sd.sig.mmtc.automation.job;

import edu.jhuapl.sd.sig.mmtc.autocorrelate.AutocorrelateApp;
import edu.jhuapl.sd.sig.mmtc.automation.AutomationContext;
import edu.jhuapl.sd.sig.mmtc.automation.config.AutomationAppConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;

import java.util.List;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.now;

public class AutocorrelateJob implements InterruptableJob {
    private static final Logger logger = LogManager.getLogger();

    private volatile AutocorrelateApp autocorrelateApp;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("Beginning AutocorrelateJob execution...");

        JobDataMap jobDataMap = context.getMergedJobDataMap();

        final AutomationAppConfig automationAppConfig = (AutomationAppConfig) jobDataMap.get("automationAppConfig");

        try {
            final AutomationContext ctx = new AutomationContext(automationAppConfig, now());

            autocorrelateApp = new AutocorrelateApp();

            List<TimeCorrelationContext> newCorrelations = this.autocorrelateApp.run();

            ctx.timeCorrelationRuns.set(newCorrelations);
            AutomationAppUtils.setAncillaryInfo(automationAppConfig, ctx);

            logger.info("AutocorrelateJob execution complete.");
        } catch (Exception e) {
            logger.error("AutocorrelateJob encountered an exception", e);
            throw new JobExecutionException(e);
        }
    }

    @Override
    public void interrupt() {
        if (autocorrelateApp != null) {
            autocorrelateApp.interrupt();
        }
    }
}
