package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcCli;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.*;

public class TimeCorrelationAppConfig extends MmtcConfig {
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

    private static final ClockChangeRateMode defaultClockChangeRateMode = ClockChangeRateMode.COMPUTE_INTERPOLATED;

    private static final Logger logger = LogManager.getLogger();

    private final CorrelationCommandLineConfig cmdLineConfig;

    private final TelemetrySource telemetrySource;

    private ClockChangeRateMode clockChangeRateMode;
    private double clockChangeRateAssignedValue;
    private AdditionalSmoothingRecordConfig additionalSmoothingRecordConfig;

    public String getCmdLineOptionValue(char shortOpt) {
        return cmdLineConfig.getOptionValue(shortOpt);
    }

    public boolean cmdLineHasOption(char shortOpt) {
        return cmdLineConfig.hasOption(shortOpt);
    }

    public String[] getCliArgs() {
        return this.cmdLineConfig.getArgs();
    }

    public enum ClockChangeRateMode {
        COMPUTE_INTERPOLATED,
        COMPUTE_PREDICTED,
        ASSIGN,
        ASSIGN_KEY,
        NO_DRIFT
    }

    public static class AdditionalSmoothingRecordConfig {
        public final boolean enabled;
        public final int coarseSclkTickDuration;

        public AdditionalSmoothingRecordConfig(boolean enabled, int coarseSclkTickDuration) {
            this.enabled = enabled;
            this.coarseSclkTickDuration = coarseSclkTickDuration;
        }
    }

    public TimeCorrelationAppConfig(String... args) throws Exception {
        super();

        this.telemetrySource = this.initTlmSource();

        this.cmdLineConfig = new CorrelationCommandLineConfig(args, this.telemetrySource.getAdditionalCliArguments());

        if (! cmdLineConfig.load()) {
            throw new MmtcException("Error parsing command line arguments.");
        }

        setClockChangeRateMode();
        if (clockChangeRateMode == ClockChangeRateMode.ASSIGN) {
            setClockChangeRateAssignedValue();
        }

        if (clockChangeRateMode == ClockChangeRateMode.ASSIGN || clockChangeRateMode == ClockChangeRateMode.ASSIGN_KEY) {
            logger.info(MmtcCli.USER_NOTICE, String.format("Assigned clock change rate: %f", clockChangeRateAssignedValue));
        }

        setInsertAdditionalSmoothingRecord();
        if (additionalSmoothingRecordConfig.enabled && clockChangeRateMode == ClockChangeRateMode.COMPUTE_INTERPOLATED) {
            throw new MmtcException("Cannot insert 'smoothing' correlation records into products with --clkchgrate-compute i");
        }

        // todo would be ideal if config was immutable
        this.telemetrySource.applyConfiguration(this);

        logger.debug(toString());
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }

    /**
     * Gets the method to be used to compute the clock change rate.
     *
     * @return the clock change rate compute method
     */
    public ClockChangeRateMode getClockChangeRateMode() {
        return clockChangeRateMode;
    }

    /**
     * Records how the clock change rate is to be computed (either predicted,
     * interpolated, assigned, or set to zero (no drift)). This is determined by the
     * command line options. If no relevant command line options are passed in, then
     * this is determined by the configuration file. If no relevant option is found
     * in the configuration file, then this falls back to a default method.
     *
     * @throws MmtcException if the configuration file contains an
     *                                  invalid value for the clock change rate mode
     *                                  option.
     */
    private void setClockChangeRateMode() throws MmtcException {
        if (cmdLineConfig.hasClockChangeRateMode()) {
            logger.info(String.format("Clock change rate mode is specified by command line argument, overriding default mode %s.", defaultClockChangeRateMode));
            clockChangeRateMode = cmdLineConfig.getClockChangeRateMode();
            if (clockChangeRateMode == ClockChangeRateMode.ASSIGN_KEY) {
                String clockChangeRatePresetKey = cmdLineConfig.getClockChangeRateAssignedKey();
                try {
                    logger.debug(String.format("Reading assigned clock change rate from config key compute.clkchgrate.assignedValuePresets.%s", clockChangeRatePresetKey));
                    clockChangeRateAssignedValue = timeCorrelationConfig.getConfig().getDouble("compute.clkchgrate.assignedValuePresets." + clockChangeRatePresetKey);
                } catch(NoSuchElementException e) {
                    throw new MmtcException("Could not find a corresponding clkchgrate preset in the config with name compute.clkchgrate.assignedValuePresets."+clockChangeRatePresetKey);
                }
            }
        } else {
            logger.info(String.format("Command line does not specify a clkchgrate mode, using default mode %s.", defaultClockChangeRateMode));
            clockChangeRateMode = defaultClockChangeRateMode;
        }
    }

    /**
     * Gets the assigned clock change rate. Calls to this method will fail unless the clock change rate mode is actually
     * ClockChangeRateMode.ASSIGN.
     *
     * @return the clock change rate value
     * @throws MmtcException if called before the clock change rate mode has been set to 'assign', or if it is not 'assign'
     */
    public double getClockChangeRateAssignedValue() throws MmtcException {
        if (getClockChangeRateMode() == null) {
            throw new MmtcException("Clock change rate mode has not been set yet");
        }

        if (! (getClockChangeRateMode().equals(ClockChangeRateMode.ASSIGN) ||  getClockChangeRateMode().equals(ClockChangeRateMode.ASSIGN_KEY))) {
            throw new MmtcException("Attempted to use the clock change rate value in clock change rate mode: " + getClockChangeRateMode());
        }

        return clockChangeRateAssignedValue;
    }

    /**
     * Records the fixed value of the clock change rate to be written to the time
     * correlation records. This value is taken from the command line if available,
     * or else from the configuration file if specified there, or else from a
     * hardcoded default. This method should only be used when the user has opted to
     * assign the clock change rate rather than compute it. This is typically done
     * in test venues or in anomalous circumstances, or when a new SCLK clock
     * partition is defined, or when there is an oscillator switch.
     */
    private void setClockChangeRateAssignedValue() throws MmtcException {
        final String assignedClockChangeRateKey = "compute.clkchgrate.assignedValue";

        if (getClockChangeRateMode() == null || (! getClockChangeRateMode().equals(ClockChangeRateMode.ASSIGN))) {
            throw new MmtcException("Attempted to set the clock change rate value in clock change rate mode: " + getClockChangeRateMode());
        }

        if (cmdLineConfig.hasClockChangeRateMode()) {
            clockChangeRateAssignedValue = cmdLineConfig.getClockChangeRateAssignedValue();
        } else {
            try {
                clockChangeRateAssignedValue = timeCorrelationConfig.getConfig().getDouble(assignedClockChangeRateKey);
            } catch (NoSuchElementException | ConversionException ex) {
                throw new MmtcException("Config file does not specify a value for " + assignedClockChangeRateKey);
            }
        }
    }

    private void setInsertAdditionalSmoothingRecord() throws MmtcException {
        final Optional<AdditionalSmoothingRecordConfig> additionalSmoothingRecordInsertionOverride = cmdLineConfig.getAdditionalSmoothingRecordInsertionOverride();

        if (additionalSmoothingRecordInsertionOverride.isPresent()) {
            this.additionalSmoothingRecordConfig = additionalSmoothingRecordInsertionOverride.get();
            return;
        }

        this.additionalSmoothingRecordConfig = new AdditionalSmoothingRecordConfig(
                isAdditionalSmoothingCorrelationRecordInsertionEnabled(),
                getAdditionalSmoothingCorrelationRecordInsertionCoarseSclkTickDuration()
        );
    }

    public AdditionalSmoothingRecordConfig getAdditionalSmoothingRecordConfig() {
        return this.additionalSmoothingRecordConfig;
    }

    /**
     * The start time of the telemetry interval from which to query data for time correlation as supplied by
     * the user in the command line arguments.
     *
     * @return the sampling interval start time supplied by the user
     */
    public OffsetDateTime getStartTime() {
        return cmdLineConfig.getStartTime();
    }

    /**
     * The stop time of the telemetry interval from which to query data for time correlation as supplied by
     * the user in the command line arguments.
     *
     * @return the sampling interval stop time supplied by the user
     */
    public OffsetDateTime getStopTime() {
        return cmdLineConfig.getStopTime();
    }

    /**
     * Indicates if test mode is set.
     *
     * @return true if test mode is enabled
     */
    public boolean isTestMode() {
        return cmdLineConfig.isTestMode();
    }

    /**
     * Gets the one-way light travel time (OWLT) value supplied by the user in the command line options in a test venue.
     *
     * @return the OWLT to be used for time correlation
     */
    public double getTestModeOwlt() { return cmdLineConfig.getTestModeOwlt(); }

    /**
     * As is the case with the other filters, the contact filter can be enabled or
     * disabled from the parameter file, which is the preferred way to do it.
     * However, it can also be disabled from the command line with the
     * --disable-contact-filter or -F command line option. The command line option
     * overrides the configuration filter.contact.enabled paramter setting.
     *
     * @return true if the contact filter is DISABLED, false if enabled
     * @throws MmtcException if contact filter is not disabled from the
     *                                  command line and is also missing from
     *                                  application configuration
     */
    public boolean isContactFilterDisabled() throws MmtcException {
        boolean isDisabled = true;
        if (!cmdLineConfig.isContactFilterDisabled()) {
            String filterName = TimeCorrelationAppConfig.CONTACT_FILTER;
            try {
                if (timeCorrelationConfig.getConfig().getBoolean(String.format("filter.%s.enabled", filterName))) {
                    logger.info("Filter " + filterName + " is enabled.");
                    isDisabled = false;
                }
                else {
                    logger.info("Filter " + filterName + " is disabled.");
                }
            }
            catch (NoSuchElementException ex) {
                throw new MmtcException("Expected contact filter " + filterName +
                        " is is not indicated configuration file", ex);
            }
        }
        return isDisabled;
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
                TimeCorrelationAppConfig.MIN_DATARATE_FILTER,
                TimeCorrelationAppConfig.MAX_DATARATE_FILTER,
                TimeCorrelationAppConfig.ERT_FILTER,
                TimeCorrelationAppConfig.GROUND_STATION_FILTER,
                TimeCorrelationAppConfig.SCLK_FILTER,
                TimeCorrelationAppConfig.VALID_FILTER,
                TimeCorrelationAppConfig.CONSEC_FRAMES_FILTER,
                TimeCorrelationAppConfig.VCID_FILTER,
                TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER,
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
     * Indicates if this is a dry run
     *
     * @return true if -D or --dry-run CLI options are invoked
     */
    public boolean isDryRun() {
        return cmdLineConfig.isDryRun();
    }

    /**
     * Indicates if an optional Uplink Command File is to be created. This can be specified in either the command line
     * or in configuration parameters. Command line overrides the configuration parameter.
     *
     * @return true if an Uplink Command File is to be created
     */
    public boolean isCreateUplinkCmdFile() {
        if (cmdLineConfig.isGenerateCmdFile()) {
            return true;
        } else {
            return timeCorrelationConfig.getConfig().getBoolean("product.uplinkCmdFile.create", false);
        }
    }

    /**
     * Validate that the configuration is complete: all required keys are present, and for any optional products that
     * are enabled, that they have their required config keys populated
     *
     * @throws MmtcException if the MMTC configuration is incomplete
     */
    public void validate() throws MmtcException {
        // Validate that all required config keys are present
        super.validate();

        if (createSclkScetFile()) {
            validateSclkScetConfiguration();
        }

        if (isCreateUplinkCmdFile()) {
            validateUplinkCmdFileConfiguration();
        }
    }
}
