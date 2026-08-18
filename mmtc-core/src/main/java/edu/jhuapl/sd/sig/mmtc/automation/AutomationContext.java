package edu.jhuapl.sd.sig.mmtc.automation;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.automation.config.AutomationAppConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.trending.TrendingReportContext;
import edu.jhuapl.sd.sig.mmtc.util.Settable;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public class AutomationContext {
    public final AutomationAppConfig config;
    public final OffsetDateTime automationRunTime;

    public final Settable<String> currentSclkKernelDescription = new Settable<>();
    public final Settable<Double> currentSclkKernelLatestTripletAgeDays = new Settable<>();

    public final Settable<List<TimeCorrelationContext>> timeCorrelationRuns = new Settable<>();
    public final Settable<TrendingReportContext> trendingReport = new Settable<>();

    public AutomationContext(AutomationAppConfig config, OffsetDateTime automationRunTime) throws MmtcException {
        this.config = config;
        this.automationRunTime = automationRunTime;
    }

    public String getGeneratedBy() {
        BuildInfo buildInfo = new BuildInfo();
        return String.format(
                "MMTC %s (%s) on %s",
                buildInfo.version,
                buildInfo.commit,
                automationRunTime
        );
    }
}
