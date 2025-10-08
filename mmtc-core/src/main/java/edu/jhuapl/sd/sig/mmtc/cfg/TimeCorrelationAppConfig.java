package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

public abstract class TimeCorrelationAppConfig extends MmtcConfigWithTlmSource {
    private static final Logger logger = LogManager.getLogger();

    public static final String CONTACT_FILTER = "contact";
    public static final String MIN_DATARATE_FILTER = "minDataRate";
    public static final String MAX_DATARATE_FILTER = "maxDataRate";
    public static final String ERT_FILTER = "ert";
    public static final String GROUND_STATION_FILTER = "groundStation";
    public static final String SCLK_FILTER = "sclk";
    public static final String VALID_FILTER = "validFlag";
    public static final String CONSEC_FRAMES_FILTER = "consecutiveFrames";
    public static final String VCID_FILTER = "vcid";
    public static final String CONSEC_MC_FRAME_FILTER = "consecutiveMasterChannelFrames";

    public enum ClockChangeRateMode {
        COMPUTE_INTERPOLATED,
        COMPUTE_PREDICTED,
        ASSIGN,
        ASSIGN_KEY,
        NO_DRIFT
    }

    protected static final TimeCorrelationAppConfig.ClockChangeRateMode defaultClockChangeRateMode = TimeCorrelationCliAppConfig.ClockChangeRateMode.COMPUTE_INTERPOLATED;

    public TimeCorrelationAppConfig() throws Exception {
        super();
    }

    public TimeCorrelationAppConfig(MmtcConfigWithTlmSource config) throws Exception {
        super(config);
    }

    /**
     * Creates a container map that holds the filters that are to be applied in this run, excluding the
     * contact filter.
     * @return a map containing names and TimeCorrelationFilter instances; note this does not include the
     *         contact filter
     * @throws MmtcException if the filter entries in the configuration parameters are incomplete
     */
    public Map<String, TimeCorrelationFilter> getFilters() throws MmtcException {
        Map<String, TimeCorrelationFilter> filters = new LinkedHashMap<>();

        // purposefully disincludes the Contact Filter, as this is handled as a special case elsewhere
        String[] expectedFilterNames = {
                TimeCorrelationCliAppConfig.MIN_DATARATE_FILTER,
                TimeCorrelationCliAppConfig.MAX_DATARATE_FILTER,
                TimeCorrelationCliAppConfig.ERT_FILTER,
                TimeCorrelationCliAppConfig.GROUND_STATION_FILTER,
                TimeCorrelationCliAppConfig.SCLK_FILTER,
                TimeCorrelationCliAppConfig.VALID_FILTER,
                TimeCorrelationCliAppConfig.CONSEC_FRAMES_FILTER,
                TimeCorrelationCliAppConfig.VCID_FILTER,
                TimeCorrelationCliAppConfig.CONSEC_MC_FRAME_FILTER,
        };

        for (String filterName : expectedFilterNames) {
            try {
                if (timeCorrelationConfig.getConfig().getBoolean(String.format("filter.%s.enabled", filterName))) {
                    TimeCorrelationFilter filter = TimeCorrelationFilter.createFilterInstanceByName(filterName);
                    filters.put(filterName, filter);
                    logger.info("Filter " + filterName + " is enabled.");
                }
                else {
                    logger.info("Filter " + filterName + " is disabled.");
                }
            } catch (NoSuchElementException ex) {
                throw new MmtcException("Expected filter " + filterName + " is missing from configuration file");
            }
        }

        return filters;
    }

    /**
     * Validate that the configuration is complete: all required keys are present, and for any optional products that
     * are enabled, that they have their required config keys populated
     *
     * @throws MmtcException if the MMTC configuration is incomplete
     */
    public void validate() throws MmtcException {
        super.validate();

        if (createSclkScetFile()) {
            validateSclkScetConfiguration();
        }
    }

    public abstract boolean isContactFilterDisabled() throws MmtcException;

    public abstract OffsetDateTime getStopTime();

    public abstract OffsetDateTime getStartTime();

    public abstract Optional<TimeCorrelationTargetIdentifier> getDesiredTargetSampleErt();

    public abstract boolean isTestMode();

    public abstract double getTestModeOwlt();

    public abstract String[] getCliArgs();

    public abstract ClockChangeRateMode getClockChangeRateMode();

    public abstract double getClockChangeRateAssignedValue() throws MmtcException;

    public static class TimeCorrelationTargetIdentifier {
        public final String targetFrameErt;
        public final String supplementalFrameErt;

        public TimeCorrelationTargetIdentifier(String targetFrameErt, String supplementalFrameErt) {
            this.targetFrameErt = targetFrameErt;
            this.supplementalFrameErt = supplementalFrameErt;
        }
    }
}
