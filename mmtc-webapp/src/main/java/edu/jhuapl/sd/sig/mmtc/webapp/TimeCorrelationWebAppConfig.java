package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.Optional;

public class TimeCorrelationWebAppConfig extends TimeCorrelationAppConfig {
    private static final Logger logger = LogManager.getLogger();
    private final CorrelationConfig correlationConfig;

    public record CorrelationConfig(
            String targetFrameErt,
            String supplementalFrameErt,
            String beginErt,
            String endErt,
            boolean isContactFilterDisabled,
            boolean isTestMode,
            double testModeOwlt,
            TimeCorrelationAppConfig.ClockChangeRateMode clockChangeRateMode,
            double clockChangeRateAssignedValue
    ) { }

    public TimeCorrelationWebAppConfig(MmtcWebAppConfig mmtcWebAppConfig, CorrelationConfig correlationConfig) throws Exception {
        super(mmtcWebAppConfig);
        this.correlationConfig = correlationConfig;
        this.telemetrySource.applyConfiguration(this);

        this.telemetrySource.checkCorrelationConfiguration(this);
    }

    @Override
    public boolean isContactFilterDisabled() {
        return correlationConfig.isContactFilterDisabled();
    }

    @Override
    public OffsetDateTime getStopTime() {
        return TimeConvert.parseIsoDoyUtcStr(correlationConfig.endErt);
    }

    @Override
    public OffsetDateTime getStartTime() {
        return TimeConvert.parseIsoDoyUtcStr(correlationConfig.beginErt);
    }

    @Override
    public Optional<TimeCorrelationTargetIdentifier> getDesiredTargetSampleErt() {
        if (correlationConfig.targetFrameErt != null && !correlationConfig.targetFrameErt.isEmpty()) {
            return Optional.of(new TimeCorrelationTargetIdentifier(
                    correlationConfig.targetFrameErt,
                    correlationConfig.supplementalFrameErt
            ));
        }

        return Optional.empty();
    }

    @Override
    public boolean isTestMode() {
        return correlationConfig.isTestMode();
    }

    @Override
    public double getTestModeOwlt() {
        return correlationConfig.testModeOwlt;
    }

    @Override
    public String[] getCliArgs() {
        return new String[]{};
    }

    @Override
    public ClockChangeRateMode getClockChangeRateMode() {
        return correlationConfig.clockChangeRateMode;
    }

    @Override
    public double getClockChangeRateAssignedValue() {
        return correlationConfig.clockChangeRateAssignedValue;
    }

    @Override
    public boolean isDryRun() {
        return false; // todo
    }

    @Override
    public boolean isCreateUplinkCmdFile() {
        return false; // todo
    }

    @Override
    public String getAdditionalOptionValue(String optionName) {
        return null; // todo should return ampcs connection args and session ID from config here
    }
}
