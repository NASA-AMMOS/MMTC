package edu.jhuapl.sd.sig.mmtc.automation.job;

import edu.jhuapl.sd.sig.mmtc.automation.AutomationContext;
import edu.jhuapl.sd.sig.mmtc.automation.config.AutomationAppConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;

import java.time.Duration;
import java.time.OffsetDateTime;

public class AutomationAppUtils {
    public static void setAncillaryInfo(AutomationAppConfig config, AutomationContext ctx) throws Exception {
        ctx.currentSclkKernelDescription.set(config.getInputSclkKernelPath().getFileName().toString());
        SclkKernel currentSclkKernel = SclkKernel.read(config.getInputSclkKernelPath());

        final OffsetDateTime lastCorrelationScetUtc = TimeConvert.tdtToUtc(currentSclkKernel.getLastTriplet().getTdt(), 6);
        ctx.currentSclkKernelLatestTripletAgeDays.set(
                Duration.between(
                        lastCorrelationScetUtc,
                        ctx.automationRunTime
                ).toMinutes() / (60.0 * 24.0)
        );
    }
}
