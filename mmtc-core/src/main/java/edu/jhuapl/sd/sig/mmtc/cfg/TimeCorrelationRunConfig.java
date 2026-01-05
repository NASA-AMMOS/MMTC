package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

// integrates the aspects of configuration that are inputs liable to change per-run, atop file-based configuration
public class TimeCorrelationRunConfig extends MmtcConfigWithTlmSource implements TimeCorrelationMetricsConfig {
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

    private final TimeCorrelationRunConfigInputs runConfigInputs;

    // resolved attributes
    private ClockChangeRateMode resolvedClockChangeRateMode;
    private double resolvedClockChangeRateAssignedValue;
    private AdditionalSmoothingRecordConfig resolvedAdditionalSmoothingRecordConfig;
    private Optional<OffsetDateTimeRange> resolvedTargetSampleRange;
    private Optional<OffsetDateTime> resolvedTargetSampleExactErt;

    public enum TargetSampleInputErtMode {
        RANGE,
        EXACT
    }

    public static class AdditionalSmoothingRecordConfig {
        public boolean enabled;
        public int coarseSclkTickDuration;

        public AdditionalSmoothingRecordConfig( ) { }

        public AdditionalSmoothingRecordConfig(boolean enabled, int coarseSclkTickDuration) {
            this.enabled = enabled;
            this.coarseSclkTickDuration = coarseSclkTickDuration;
        }

        public String toLogString() {
            if (this.enabled) {
                return "Additional smoothing record insertion = enabled at " + this.coarseSclkTickDuration;
            } else {
                return "Additional smoothing record insertion = disabled";
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            AdditionalSmoothingRecordConfig that = (AdditionalSmoothingRecordConfig) o;
            return enabled == that.enabled && coarseSclkTickDuration == that.coarseSclkTickDuration;
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, coarseSclkTickDuration);
        }
    }

    public enum DryRunMode {
        NOT_DRY_RUN,
        DRY_RUN_RETAIN_NO_PRODUCTS,
        DRY_RUN_GENERATE_SEPARATE_SCLK_ONLY
    }

    public static class DryRunConfig {
        public DryRunMode mode;
        public Path sclkKernelOutputPath;

        public DryRunConfig() { }

        public DryRunConfig(DryRunMode mode, Path sclkKernelOutputPath) {
            this.mode = mode;
            this.sclkKernelOutputPath = sclkKernelOutputPath;
        }
    }



    // ideas for encoding this into the run history file
    // - still encode this into a 'run args' column, implying a means to convert the data in an instance of this class into its CLI equivalent
    // - maybe consider a separate column for the start & stop / exact ert input time this was run with

    public static class TimeCorrelationRunConfigInputs {
        // inputs that must be provided with each run, which are not contained in configuration files at all
        final TargetSampleInputErtMode targetSampleInputErtMode;
        final Optional<OffsetDateTime> targetSampleRangeStartErt;
        final Optional<OffsetDateTime> targetSampleRangeStopErt;
        final Optional<OffsetDateTime> targetSampleExactErt;
        final Optional<Double> priorCorrelationExactTdt;
        final boolean testModeOwltEnabled;
        final Optional<Double> testModeOwltSec;
        final Optional<Double> clockChangeRateAssignedValue;
        final Optional<String> clockChangeRateAssignedKey;
        final DryRunConfig dryRunConfig;

        // inputs that can override those specified in configuration files
        final Optional<ClockChangeRateMode> clockChangeRateModeOverride;
        final Optional<AdditionalSmoothingRecordConfig> additionalSmoothingRecordConfigOverride;
        final boolean isDisableContactFilter;
        final boolean isCreateUplinkCmdFile;
        final List<TelemetrySource.ParsedAdditionalOption> additionalTlmSourceOptions;

        public TimeCorrelationRunConfigInputs(
                TargetSampleInputErtMode targetSampleInputErtMode,
                Optional<OffsetDateTime> targetSampleRangeStartErt,
                Optional<OffsetDateTime> targetSampleRangeStopErt,
                Optional<OffsetDateTime> targetSampleExactErt,
                Optional<Double> priorCorrelationExactTdt,
                boolean testModeOwltEnabled,
                Optional<Double> testModeOwltSec,
                Optional<Double> clockChangeRateAssignedValue,
                Optional<String> clockChangeRateAssignedKey,
                Optional<ClockChangeRateMode> clockChangeRateModeOverride,
                Optional<AdditionalSmoothingRecordConfig> additionalSmoothingRecordConfigOverride,
                boolean isDisableContactFilter,
                boolean isCreateUplinkCmdFile,
                DryRunConfig dryRunConfig,
                List<TelemetrySource.ParsedAdditionalOption> additionalTlmSourceOptions
        ) {
            this.targetSampleInputErtMode = targetSampleInputErtMode;
            this.targetSampleRangeStartErt = targetSampleRangeStartErt;
            this.targetSampleRangeStopErt = targetSampleRangeStopErt;
            this.targetSampleExactErt = targetSampleExactErt;
            this.priorCorrelationExactTdt = priorCorrelationExactTdt;
            this.testModeOwltEnabled = testModeOwltEnabled;
            this.testModeOwltSec = testModeOwltSec;
            this.clockChangeRateAssignedValue = clockChangeRateAssignedValue;
            this.clockChangeRateAssignedKey = clockChangeRateAssignedKey;
            this.clockChangeRateModeOverride = clockChangeRateModeOverride;
            this.additionalSmoothingRecordConfigOverride = additionalSmoothingRecordConfigOverride;
            this.isDisableContactFilter = isDisableContactFilter;
            this.isCreateUplinkCmdFile = isCreateUplinkCmdFile;
            this.dryRunConfig = dryRunConfig;
            this.additionalTlmSourceOptions = additionalTlmSourceOptions;
        }
    }

    public TimeCorrelationRunConfig(TimeCorrelationRunConfigInputSupplier runConfigInputSupplier) throws Exception {
        super();
        this.runConfigInputs = runConfigInputSupplier.getRunConfigInputs(this.getTelemetrySource().getAdditionalOptions());
        resolveCalculatedRunConfigInputs();
        this.telemetrySource.applyConfiguration(this);
        for (TelemetrySource.ParsedAdditionalOption opt : this.runConfigInputs.additionalTlmSourceOptions) {
            if (opt.value.isPresent()) {
                this.telemetrySource.applyOption(opt.name, opt.value.get());
            }
        }
    }

    public TimeCorrelationRunConfig(TimeCorrelationRunConfigInputSupplier runConfigInputSupplier, MmtcConfigWithTlmSource config) throws Exception {
        super(config);
        this.runConfigInputs = runConfigInputSupplier.getRunConfigInputs(this.getTelemetrySource().getAdditionalOptions());
        resolveCalculatedRunConfigInputs();
        for (TelemetrySource.ParsedAdditionalOption opt : this.runConfigInputs.additionalTlmSourceOptions) {
            if (opt.value.isPresent()) {
                this.telemetrySource.applyOption(opt.name, opt.value.get());
            }
        }
    }

    private void resolveCalculatedRunConfigInputs() throws MmtcException {
        setTargetAndBasisSampleInputs();
        setClockChangeRateConfiguration();
        setInsertAdditionalSmoothingRecordConfiguration();

        // ensure the given path for a dry run SCLK kernel file is NOT the same directory as the configured output directory
        if (getDryRunConfig().mode.equals(DryRunMode.DRY_RUN_GENERATE_SEPARATE_SCLK_ONLY)) {
            Path sclkKernelDryRunOutputPath = getDryRunConfig().sclkKernelOutputPath;
            if (getSclkKernelOutputDir().toAbsolutePath().startsWith(sclkKernelDryRunOutputPath)) {
                throw new IllegalStateException("The output SCLK kernel directory must be distinct from the configured SCLK kernel output directory: " + getSclkKernelOutputDir().toAbsolutePath());
            }
        }

        if (resolvedAdditionalSmoothingRecordConfig.enabled && resolvedClockChangeRateMode == ClockChangeRateMode.COMPUTE_INTERPOLATE) {
            throw new MmtcException("Cannot insert 'smoothing' correlation records into products with --clkchgrate-compute i");
        }

        this.telemetrySource.checkCorrelationConfiguration(this);
    }

    private void setTargetAndBasisSampleInputs() {
        if (this.runConfigInputs.targetSampleInputErtMode.equals(TargetSampleInputErtMode.RANGE)) {
            this.resolvedTargetSampleRange = Optional.of(new OffsetDateTimeRange(this.runConfigInputs.targetSampleRangeStartErt.get(), this.runConfigInputs.targetSampleRangeStopErt.get()));
            this.resolvedTargetSampleExactErt = Optional.empty();
        } else {
            this.resolvedTargetSampleExactErt = this.runConfigInputs.targetSampleExactErt;
            this.resolvedTargetSampleRange = Optional.empty();
        }
    }

    /**
     * Records how the clock change rate is to be computed (either predicted,
     * interpolated, assigned, or set to the identity rate (no drift)). This is determined by the
     * command line options. If no relevant command line options are passed in, then
     * this is determined by the configuration file. If no relevant option is found
     * in the configuration file, then this falls back to a default value.
     *
     * @throws MmtcException
     */
    private void setClockChangeRateConfiguration() throws MmtcException {
        if (runConfigInputs.clockChangeRateModeOverride.isPresent()) {
            resolvedClockChangeRateMode = runConfigInputs.clockChangeRateModeOverride.get();

            // the 'assign' and 'nodrift' modes can only be set via runtime user input; check for these first
            if (isClockChangeRateModeAssign()) {
                setAssignedClockChangeRateConfiguration();
                logger.info(USER_NOTICE, String.format("Clock change rate mode is specified by input: %s with rate %f", resolvedClockChangeRateMode, getClockChangeRateAssignedValue()));
            } else {
                // nothing else to do here for the other user-specified modes
                logger.info(USER_NOTICE, String.format("Clock change rate mode is specified by input: %s", resolvedClockChangeRateMode));
            }

            return;
        }

        resolvedClockChangeRateMode = getConfiguredClockChangeRateMode();
        logger.info(USER_NOTICE, String.format("Clock change rate mode: %s", resolvedClockChangeRateMode));
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
    private void setAssignedClockChangeRateConfiguration() throws MmtcException {
        if (! isClockChangeRateModeAssign()) {
            throw new MmtcException("Clock change rate mode is not one of the 'assign' modes");
        }

        if (resolvedClockChangeRateMode == ClockChangeRateMode.ASSIGN) {
            resolvedClockChangeRateAssignedValue = this.runConfigInputs.clockChangeRateAssignedValue.get();
        } else if (resolvedClockChangeRateMode == ClockChangeRateMode.ASSIGN_KEY) {
            String clockChangeRatePresetKey = this.runConfigInputs.clockChangeRateAssignedKey.get();
            try {
                logger.debug(String.format("Reading assigned clock change rate from config key compute.clkchgrate.assignedValuePresets.%s", clockChangeRatePresetKey));
                resolvedClockChangeRateAssignedValue = timeCorrelationConfig.getConfig().getDouble("compute.clkchgrate.assignedValuePresets." + clockChangeRatePresetKey);
            } catch (NoSuchElementException e) {
                throw new MmtcException("Could not find a corresponding clkchgrate preset in the config with name compute.clkchgrate.assignedValuePresets." + clockChangeRatePresetKey);
            }
        }
    }

    public boolean isClockChangeRateModeAssign() {
        return CLOCK_CHANGE_RATE_ASSIGN_MODES.contains(resolvedClockChangeRateMode);
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

        return resolvedClockChangeRateAssignedValue;
    }

    private void setInsertAdditionalSmoothingRecordConfiguration() throws MmtcException {
        if (runConfigInputs.additionalSmoothingRecordConfigOverride.isPresent()) {
            this.resolvedAdditionalSmoothingRecordConfig = runConfigInputs.additionalSmoothingRecordConfigOverride.get();
            return;
        }

        this.resolvedAdditionalSmoothingRecordConfig = getAdditionalSmoothingRecordConfigFromFile();
    }

    private AdditionalSmoothingRecordConfig getAdditionalSmoothingRecordConfigFromFile() throws MmtcException {
        return new AdditionalSmoothingRecordConfig(
                isAdditionalSmoothingCorrelationRecordInsertionEnabled(),
                getAdditionalSmoothingCorrelationRecordInsertionCoarseSclkTickDuration()
        );
    }


    public AdditionalSmoothingRecordConfig getAdditionalSmoothingRecordConfig() {
        return this.resolvedAdditionalSmoothingRecordConfig;
    }

    /**
     * Indicates if test mode is set.
     *
     * @return true if test mode is enabled
     */
    public boolean isTestMode() {
        return runConfigInputs.testModeOwltEnabled;
    }

    /**
     * Gets the one-way light travel time (OWLT) value supplied by the user in the command line options in a test venue.
     *
     * @return the OWLT to be used for time correlation
     */
    public double getTestModeOwlt() { return runConfigInputs.testModeOwltSec.get(); }

    public Optional<Double> getPriorCorrelationTdt() {
        return runConfigInputs.priorCorrelationExactTdt;
    }

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
        if (! runConfigInputs.isDisableContactFilter) {
            String filterName = CONTACT_FILTER;
            try {
                if (timeCorrelationConfig.getConfig().getBoolean(String.format("filter.%s.enabled", filterName))) {
                    logger.info("Filter " + filterName + " is enabled.");
                    isDisabled = false;
                } else {
                    logger.info("Filter " + filterName + " is disabled.");
                }
            } catch (NoSuchElementException ex) {
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
                MIN_DATARATE_FILTER,
                MAX_DATARATE_FILTER,
                ERT_FILTER,
                GROUND_STATION_FILTER,
                SCLK_FILTER,
                VALID_FILTER,
                CONSEC_FRAMES_FILTER,
                VCID_FILTER,
                CONSEC_MC_FRAME_FILTER,
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

    public boolean isCreateUplinkCmdFile() {
        if (this.runConfigInputs.isCreateUplinkCmdFile) {
            return true;
        }

        return super.isCreateUplinkCmdFile();
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
            ensureValidSclkScetConfiguration();
        }

        if (isCreateUplinkCmdFile()) {
            ensureValidUplinkCmdFileConfiguration();
        }
    }

    public boolean isDryRun() {
        return ! this.runConfigInputs.dryRunConfig.mode.equals(DryRunMode.NOT_DRY_RUN);
    }

    public DryRunConfig getDryRunConfig() {
        return this.runConfigInputs.dryRunConfig;
    }

    public ClockChangeRateMode getClockChangeRateMode() {
        return resolvedClockChangeRateMode;
    }

    public Optional<OffsetDateTimeRange> getResolvedTargetSampleRange() {
        return resolvedTargetSampleRange;
    }

    public Optional<OffsetDateTime> getResolvedTargetSampleExactErt() {
        return resolvedTargetSampleExactErt;
    }

    public TargetSampleInputErtMode getTargetSampleInputErtMode() {
        if (resolvedTargetSampleRange.isPresent()) {
            return TargetSampleInputErtMode.RANGE;
        }

        return TargetSampleInputErtMode.EXACT;
    }

    public String getInvocationStringRepresentation() throws MmtcException {
        List<String> elts = new ArrayList<>();

        if (getTargetSampleInputErtMode().equals(TargetSampleInputErtMode.RANGE)) {
            OffsetDateTimeRange resolvedRange = getResolvedTargetSampleRange().get();
            elts.add(String.format("Input ERT range = %s to %s", TimeConvert.timeToIsoUtcString(resolvedRange.getStart(), 3), TimeConvert.timeToIsoUtcString(resolvedRange.getStop(), 3)));
        } else if (getTargetSampleInputErtMode().equals(TargetSampleInputErtMode.EXACT)) {
            elts.add(String.format("Input ERT = %s", TimeConvert.timeToIsoUtcString(getResolvedTargetSampleExactErt().get(), 3)));
        } else {
            throw new IllegalStateException("Unknown mode: " + getTargetSampleInputErtMode());
        }

        if (getPriorCorrelationTdt().isPresent()) {
            elts.add(String.format("Prior corr TDT = %s", getPriorCorrelationTdt().get()));
        }

        if (isTestMode()) {
            elts.add(String.format("Test Mode OWLT = %f", getTestModeOwlt()));
        }

        if (isDryRun()) {
            elts.add("Dry run = true");
        }

        if (runConfigInputs.clockChangeRateModeOverride.isPresent()) {
            if (! runConfigInputs.clockChangeRateModeOverride.get().equals(getConfiguredClockChangeRateMode())) {
                elts.add(String.format("Clock change rate mode = %s", runConfigInputs.clockChangeRateModeOverride.get()));

                ClockChangeRateMode overrideMode = runConfigInputs.clockChangeRateModeOverride.get();
                if (overrideMode.equals(ClockChangeRateMode.ASSIGN_KEY)) {
                    elts.add(String.format("Clock change rate assign key = %s", runConfigInputs.clockChangeRateAssignedKey.get()));
                } else if (overrideMode.equals(ClockChangeRateMode.ASSIGN)) {
                    elts.add(String.format("Clock change rate assign = %s", runConfigInputs.clockChangeRateAssignedValue.get()));
                }
            }
        }

        if (runConfigInputs.additionalSmoothingRecordConfigOverride.isPresent()) {
            if (! runConfigInputs.additionalSmoothingRecordConfigOverride.get().equals(getAdditionalSmoothingRecordConfigFromFile())) {
                elts.add(runConfigInputs.additionalSmoothingRecordConfigOverride.get().toLogString());
            }
        }

        if (runConfigInputs.isDisableContactFilter) {
            elts.add("Contact filter = disabled");
        }

        if (runConfigInputs.isCreateUplinkCmdFile) {
            elts.add("Uplink cmd file creation = enabled");
        }

        runConfigInputs.additionalTlmSourceOptions.forEach(parsedAdditionalOption -> {
            if (parsedAdditionalOption.value.isPresent()) {
                elts.add(String.format("%s = %s", parsedAdditionalOption.name, parsedAdditionalOption.value.get()));
            }
        });

        return String.join(" | ", elts);
    }

    public static class ClockChangeRateConfig {
        public TimeCorrelationRunConfig.ClockChangeRateMode clockChangeRateModeOverride;
        public double specifiedClockChangeRateToAssign;

        public ClockChangeRateConfig() { }

        public ClockChangeRateConfig(ClockChangeRateMode clockChangeRateModeOverride, double specifiedClockChangeRateToAssign) {
            this.clockChangeRateModeOverride = clockChangeRateModeOverride;
            this.specifiedClockChangeRateToAssign = specifiedClockChangeRateToAssign;
        }
    }
}
