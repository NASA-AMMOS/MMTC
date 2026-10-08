package edu.jhuapl.sd.sig.mmtc.automation.job;

import edu.jhuapl.sd.sig.mmtc.autocorrelate.AutocorrelateApp;
import edu.jhuapl.sd.sig.mmtc.automation.AutomationContext;
import edu.jhuapl.sd.sig.mmtc.automation.config.AutomationAppConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.trending.TrendingApp;
import edu.jhuapl.sd.sig.mmtc.trending.TrendingReportContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;

import java.util.List;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.now;

public class AutocorrelateAndTrendingJob implements InterruptableJob {
    private static final Logger logger = LogManager.getLogger();

    private volatile AutocorrelateApp autocorrelateApp;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("Beginning AutocorrelateAndTrendingJob execution...");

        JobDataMap jobDataMap = context.getMergedJobDataMap();

        final AutomationAppConfig automationAppConfig = (AutomationAppConfig) jobDataMap.get("automationAppConfig");

        try {
            final AutomationContext ctx = new AutomationContext(automationAppConfig, now());

            autocorrelateApp = new AutocorrelateApp();

            List<TimeCorrelationContext> newCorrelations = autocorrelateApp.run();
            ctx.timeCorrelationRuns.set(newCorrelations);

            TrendingReportContext trendingReport = new TrendingApp().run();
            ctx.trendingReport.set(trendingReport);

            AutomationAppUtils.setAncillaryInfo(automationAppConfig, ctx);
            logger.info("AutocorrelateAndTrendingJob execution complete.");
        } catch (Exception e) {
            logger.error("AutocorrelateAndTrendingJob encountered an exception", e);
            throw new JobExecutionException(e);
        }
    }

    @Override
    public void interrupt() {
        if (autocorrelateApp != null) {
            autocorrelateApp.interrupt();
        }
        // TrendingApp is not interruptable
    }
}
