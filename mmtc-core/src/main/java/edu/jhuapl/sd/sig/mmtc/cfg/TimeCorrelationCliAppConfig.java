package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcCli;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.apache.commons.cli.Option;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TimeCorrelationCliAppConfig extends TimeCorrelationAppConfig {
    private static final Logger logger = LogManager.getLogger();

    private final CorrelationCommandLineConfig cmdLineConfig;

    private ClockChangeRateMode clockChangeRateMode;

    private double clockChangeRateAssignedValue;

    public String getCmdLineOptionValue(String shortOpt) {
        return cmdLineConfig.getOptionValue(shortOpt);
    }

    public boolean cmdLineHasOption(char shortOpt) {
        return cmdLineConfig.hasOption(shortOpt);
    }

    @Override
    public String[] getCliArgs() {
        return this.cmdLineConfig.getArgs();
    }

    public TimeCorrelationCliAppConfig(String... args) throws Exception {
        super();

        this.cmdLineConfig = new CorrelationCommandLineConfig(
                args,
                this.additionalOptionsByName.values().stream().map(additionalOption -> additionalOption.cliOption).collect(Collectors.toList())
        );

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

        // todo would be ideal if config was immutable
        this.telemetrySource.applyConfiguration(this);

        logger.debug(toString());
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
    @Override
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

    /**
     * The start time of the telemetry interval from which to query data for time correlation as supplied by
     * the user in the command line arguments.
     *
     * @return the sampling interval start time supplied by the user
     */
    public OffsetDateTime getStartTime() {
        return cmdLineConfig.getStartTime();
    }

    @Override
    public Optional<TimeCorrelationTargetIdentifier> getDesiredTargetSampleErt() {
        return Optional.empty();
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
    @Override
    public boolean isContactFilterDisabled() throws MmtcException {
        boolean isDisabled = true;
        if (!cmdLineConfig.isContactFilterDisabled()) {
            String filterName = TimeCorrelationCliAppConfig.CONTACT_FILTER;
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
     * Indicates if an optional Uplink Command File is to be created. This can be specified in either the command line
     * or in configuration parameters. Command line overrides the configuration parameter.
     *
     * @return true if an Uplink Command File is to be created
     */
    @Override
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

        if (isCreateUplinkCmdFile()) {
            validateUplinkCmdFileConfiguration();
        }
    }

    @Override
    public String getAdditionalOptionValue(String optionName) {
        return getCmdLineOptionValue(additionalOptionsByName.get(optionName).cliOption.getOpt());
    }

    /**
     * Indicates if this is a dry run
     *
     * @return true if -D or --dry-run CLI options are invoked
     */
    @Override
    public boolean isDryRun() {
        return cmdLineConfig.isDryRun();
    }


}
