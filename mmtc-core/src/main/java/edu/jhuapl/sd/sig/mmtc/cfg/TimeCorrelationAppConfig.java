package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.jhuapl.sd.sig.mmtc.products.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.util.IsolatingUrlClassLoader;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Defines a wrapper of the configuration parameters for the entire time correlation. These include the
 * parameters read from the TimeCorrelationConfigProperties.xml file, the options provided buy a user from
 * the command line, Ground Station Map associations, and the SCLK Partition Map associations.
 *
 * Functions in this class also provide access to each item in the TimeCorrelationConfigProperties.xml
 * configuration parameters file.
 */
public class TimeCorrelationAppConfig {
    private static final Set<String> BUILT_IN_TLM_SOURCES = new HashSet<>(Collections.singletonList("rawTlmTable"));

    public String getCmdLineOptionValue(char shortOpt) {
        return cmdLineConfig.getOptionValue(shortOpt);
    }

    public boolean cmdLineHasOption(char shortOpt) {
        return cmdLineConfig.hasOption(shortOpt);
    }

    public enum ClockChangeRateMode {
        COMPUTE_INTERPOLATED,
        COMPUTE_PREDICTED,
        ASSIGN,
        ASSIGN_KEY,
        NO_DRIFT
    }

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

    private final CommandLineConfig cmdLineConfig;
    private final TimeCorrelationConfig timeCorrelationConfig;
    private final TelemetrySource telemetrySource;

    private GroundStationMap groundStationMap;
    private SclkPartitionMap sclkPartitionMap;
    private ClockChangeRateMode clockChangeRateMode;
    private double clockChangeRateAssignedValue;

    private static final Logger logger = LogManager.getLogger();
    private static final ClockChangeRateMode defaultClockChangeRateMode = ClockChangeRateMode.COMPUTE_INTERPOLATED;

    public TimeCorrelationAppConfig(String... args) throws Exception {
        logger.debug("Loading configuration");

        this.timeCorrelationConfig = new TimeCorrelationXmlPropertiesConfig();

        if (! timeCorrelationConfig.load()) {
            throw new MmtcException("Error loading " + timeCorrelationConfig.getPath());
        }

        this.telemetrySource = this.initTlmSource();

        this.cmdLineConfig = new CommandLineConfig(args, this.telemetrySource.getAdditionalCliArguments());

        if (!cmdLineConfig.load()) {
            throw new MmtcException("Error parsing command line arguments.");
        }

        setClockChangeRateMode();
        if (clockChangeRateMode == ClockChangeRateMode.ASSIGN) {
            setClockChangeRateAssignedValue();
        }
        if (clockChangeRateMode == ClockChangeRateMode.ASSIGN || clockChangeRateMode == ClockChangeRateMode.ASSIGN_KEY) {
            logger.info(TimeCorrelationApp.USER_NOTICE, String.format("Assigned clock change rate: %f", clockChangeRateAssignedValue));
        }

        // These configuration items need to be created here because the configuration parameters must be loaded first.
        this.groundStationMap = new GroundStationMap(getGroundStationMapPath());
        this.sclkPartitionMap = new SclkPartitionMap(getSclkPartitionMapPath());

        if (!groundStationMap.load()) {
            throw new MmtcException("Error loading " + groundStationMap.getPath());
        }
        logger.info("Loaded ground stations map " + groundStationMap.getPath());

        if (!sclkPartitionMap.load()) {
            throw new MmtcException("Error loading " + sclkPartitionMap.getPath());
        }
        logger.info("Loaded SCLK clock partition map " + sclkPartitionMap.getPath());

        // todo would be ideal if config was immutable
        this.telemetrySource.applyConfiguration(this);

        logger.debug(toString());
    }

    public TimeCorrelationAppConfig() throws Exception {
        // Minimal constructor for use during rollback
        this.timeCorrelationConfig = new TimeCorrelationXmlPropertiesConfig();
        this.telemetrySource = null;
        this.cmdLineConfig = null;

        if (! timeCorrelationConfig.load()) {
            throw new MmtcException("Error loading " + timeCorrelationConfig.getPath());
        }
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }

    /**
     * Use Java ServiceLoader to find and load a telemetry source
     */
    private TelemetrySource initTlmSource() throws Exception {
        TelemetrySource tlmSource = null;

        if (BUILT_IN_TLM_SOURCES.contains(getTelemetrySourceName())) {
            // load tlm plugin from mmtc-core implementation already available to classloaders via current classpath
            final ServiceLoader<TelemetrySource> tlmSourceLoader = ServiceLoader.load(TelemetrySource.class);

            for (TelemetrySource tlmSourceCandidate : tlmSourceLoader) {
                logger.debug("Found telemetry source candidate: {}", tlmSourceCandidate.getName());
                if (tlmSourceCandidate.getName().equals(getTelemetrySourceName())) {
                    tlmSource = tlmSourceCandidate;
                    break;
                }
            }
        } else {
            final Path pluginJarPath;

            try (Stream<Path> files = Files.list(getTelemetrySourcePluginDirectory())) {
                List<Path> results = files.filter(p -> p.toString().endsWith(".jar"))
                        .filter(p -> p.getFileName().toString().startsWith(getTelemetrySourcePluginJarPrefix()))
                        .collect(Collectors.toList());

                if (results.size() != 1) {
                    throw new MmtcException(String.format("A unique plugin jar was not found at the given location: %d matching jars found at %s", results.size(), getTelemetrySourcePluginDirectory()));
                }

                pluginJarPath = results.get(0);
            }

            // load tlm plugin from an external jar
            logger.info("Loading telemetry source implementation from {}", pluginJarPath);

            final URL[] urls = { pluginJarPath.toAbsolutePath().toUri().toURL() };

            // not closing this classloader here, because if we did, further classes won't be able to be loaded from the jar file
            URLClassLoader cl = new IsolatingUrlClassLoader(urls);
            final ServiceLoader<TelemetrySource> tlmSourceLoader = ServiceLoader.load(TelemetrySource.class, cl);

            for (TelemetrySource tlmSourceCandidate : tlmSourceLoader) {
                logger.debug("Found telemetry source candidate: {}", tlmSourceCandidate.getName());
                if (tlmSourceCandidate.getName().equals(getTelemetrySourceName())) {
                    tlmSource = tlmSourceCandidate;
                    break;
                }
            }
        }

        if (tlmSource == null) {
            throw new MmtcException(String.format("Cannot find telemetry source with name %s; please check configuration and plugin availability", getTelemetrySourceName()));
        }

        logger.info("Loaded telemetry source {}", tlmSource.getName());
        return tlmSource;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Time Correlation config file contents:\n");
        builder.append("--------------------------------------\n");
        builder.append(ConfigurationUtils.toString(timeCorrelationConfig.getConfig()));
        builder.append("\n--------------------------------------\n");
        return builder.toString();
    }

    /**
     * Checks if a key is present in timeCorrelationConfig.
     *
     * @param key The full name of the config key in TimeCorrelationConfigProperties.xml
     * @return true or false, depending on whether the key is present with a non-empty value
     */
    public boolean containsKey(String key) {
        return timeCorrelationConfig.getConfig().containsKey(key) && (! timeCorrelationConfig.getConfig().getString(key).isEmpty());
    }

    /**
     * Retrieves from the Ground Stations Map file the associations between Path ID and ground station ID.
     * Converts a Path ID received from query metadata or ground receipt header to the equivalent Ground Station
     * antenna (e.g., Path ID 14 = DSS-14 = Goldstone 70m dish). This can also relate to testbeds and front end
     * processors.
     *
     * @param pathId the path identifier to be associated with a station ID
     * @return the ground station ID
     * @throws MmtcException if the ground station is not found
     */
    public String getStationId(int pathId) throws MmtcException {
        return groundStationMap.getStationId(pathId);
    }

    /**
     * Retrieves from the SCLK Partition Map File the start times of each SCLK partition defined for the
     * mission and then determines which partition the supplied ERT falls into.
     *
     * @param groundReceiptTime the ERT associated with the partition
     * @return the SCLK clock partition number associated with the ERT
     */
    public int getSclkPartition(OffsetDateTime groundReceiptTime) {
        return sclkPartitionMap.getSclkPartition(groundReceiptTime);
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
     * Indicates if this is a dry run
     *
     * @return true if -D or --dry-run CLI options are invoked
     */
    public boolean isDryRun() {
        return cmdLineConfig.isDryRun();
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
        if(!cmdLineConfig.isContactFilterDisabled()) {
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

    /**
     * Gets the name of the mission from configuration parameters.
     * @return the name of the mission as a string
     */
    public String getMissionName() {
        return timeCorrelationConfig.getConfig().getString("missionName");
    }

    /**
     * Gets the NAIF assigned spacecraft ID from configuration parameters. This is usually the same as the
     * SANA assigned ID, except with a negative sign attached to it.
     *
     * @return the assigned NAIF spacecraft ID for the mission
     */
    public int getNaifSpacecraftId() {
        return timeCorrelationConfig.getConfig().getInt("spice.naifSpacecraftId");
    }

    /**
     * Get the number of time correlation data samples designated to constitute a sample set.
     *
     * @return the number of samples per set
     */
    public int getSamplesPerSet() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.samplesPerSet");
    }

    /**
     * Get the number of samples separating the target sample from the supplemental sample for the mission. This
     * value is usually fixed for a mission. The target sample is the sample being used for time correlation; however it
     * does not contain its own SCLK values. The SCLK values are contained in a succeding sample called the Supplemental
     * Sample. Usually (but not always), the Supplemental Sample immediately follows the Target Sample so this value is
     * usually 1.
     *
     * @return the index of the supplemental sample as an offset from the target sample
     */
    public int getSupplementalSampleOffset() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.supplementalSampleOffset");
    }

    /**
     * Get the maximum virtual channel frame counter value a frame can be assigned before the next frame's virtual
     * channel frame index is assigned zero. Depending on mission, this can range anywhere from a few hundred to 
     * a few million, so MMTC needs to know when to expect a VCFC rollover.
     *
     * @return the maximum virtual channel frame counter value before the counter rolls over to 0
     */
    public int getVcfcMaxValue() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.vcfcMaxValue");
    }

    /*
     * Gets the spacecraft's maximum MCFC value, after which the counter will roll back to 0 and begin
     * counting anew.
     *
     * @return the spacecraft maximum MCFC value
     */
    public int getMcfcMaxValue() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.mcfcMaxValue");
    }

    public boolean containsAllNonEmptyKeys(String... keys) {
        for (String k : keys) {
            if (! containsNonEmptyKey(k)) {
                return false;
            }
        }

        return true;
    }

    /*
     * Checks the underlying file configuration for the presence of a non-empty key
     *
     * @return whether the key exists and is not empty
     */
    public boolean containsNonEmptyKey(String key) {
        return timeCorrelationConfig.getConfig().containsKey(key) && (! getString(key).trim().isEmpty());
    }

    /**
     * Creates a container in the form of a map that holds the SPICE kernels to load.
     * @return a map containing the types and paths of each SPICE kernel
     * @throws MmtcException if the list of kernels to load could not be obtained from configuration data
     */
    public Map<String, String> getKernelsToLoad() throws MmtcException {
        Map<String, String> kernels = new LinkedHashMap<>();

        // Input metakernel
        if (timeCorrelationConfig.getConfig().containsKey("spice.kernel.mk.path")) {
            kernels.put(timeCorrelationConfig.getConfig().getString("spice.kernel.mk.path"), "mk");
        }

        // Get the path to the SCLK kernel. This will be the highest versioned SCLK kernel that matches
        // the file pattern given in the configuration parameters, unless spice.kernel.sclk.inputPathOverride
        // is set in the configuration parameters. The inputPathOverride parameter overrides the normal
        // previous SCLK kernel search.
        kernels.put(getSclkKernelPath().toString(), "sclk");

        // Leap seconds kernel
        if (timeCorrelationConfig.getConfig().containsKey("spice.kernel.lsk.path")) {
            kernels.put(timeCorrelationConfig.getConfig().getString("spice.kernel.lsk.path"), "lsk");
        }

        List<String> illegalCommas = new ArrayList<>();
        String key;
        String[] paths;

        // SPK kernels
        key = "spice.kernel.spk.path";
        for (String spk : timeCorrelationConfig.getConfig().getStringArray(key)) {
            if (spk.isEmpty()) {
                illegalCommas.add(key);
                break;
            }
            kernels.put(spk, "spk");
        }

        // PCK kernels
        key = "spice.kernel.pck.path";
        for (String pck : timeCorrelationConfig.getConfig().getStringArray(key)) {
            if (pck.isEmpty()) {
                illegalCommas.add(key);
                break;
            }
            kernels.put(pck, "pck");
        }

        // FK kernels
        key = "spice.kernel.fk.path";
        for (String fk : timeCorrelationConfig.getConfig().getStringArray(key)) {
            if (fk.isEmpty()) {
                illegalCommas.add(key);
                break;
            }
            kernels.put(fk, "fk");
        }

        if (!illegalCommas.isEmpty()) {
            String message = "The following kernel configuration options contain consecutive and/or trailing commas. These configurations are allowed to contain one or more paths separated by commas; however, they are not allowed to contain empty paths. This means they must not contain multiple commas in a row (even if the commas are separated by whitespace or line breaks), and they must not end with a comma.";
            for (String config : illegalCommas) {
                message += "\n  " + config;
            }
            logger.fatal(message);
            throw new MmtcException("Consecutive or trailing commas in kernel configurations");
        }

        return kernels;
    }

    public String getTelemetrySourceName() {
        return timeCorrelationConfig.getConfig().getString("telemetry.source.name");
    }

    public Path getTelemetrySourcePluginDirectory() {
        return Paths.get(timeCorrelationConfig.getConfig().getString("telemetry.source.pluginDirectory")).toAbsolutePath();
    }

    public String getTelemetrySourcePluginJarPrefix() {
        return timeCorrelationConfig.getConfig().getString("telemetry.source.pluginJarPrefix");
    }

    /**
     * Gets the telemetry source object. This is the abstract object that could be a telemetry server or a file.
     *
     * @return the URI of the SCLK/ERT data source
     * @throws MmtcException if the telemetry source could not be determined
     */
    public URI getTelemetrySourceUri() throws MmtcException {
        try {
            return new URI(timeCorrelationConfig.getConfig().getString("telemetry.source.uri"));
        }
        catch (URISyntaxException ex) {
            throw new MmtcException("Unable to parse URI", ex);
        }
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
     * Gets the upper clock drift rate threshold for the Contact Filter
     * @return the clock drift rate delta upper threshold for the contact filter.
     */
    public double getContactFilterDeltaUpperThreshold() {
        return timeCorrelationConfig.getConfig().getDouble("filter.contact.deltaUpperThreshold");
    }

    /**
     * Gets the lower clock drift rate threshold for the Contact Filter.
     * @return the clock change rate delta lower threshold for the contact filter.
     */
    public double getContactFilterDeltaLowerThreshold() {
        return timeCorrelationConfig.getConfig().getDouble("filter.contact.deltaLowerThreshold");
    }

    /**
     * Gets the minimum data rate in bits per second that a sample must have been received at in order
     * to be used in time correlation as passed through the Minimum Data Rate Filter.
     * @return the minimum accepted data rate
     */
    public int getDataRateFilterMinRateBps() {
        return timeCorrelationConfig.getConfig().getInt("filter.dataRate.minDataRateBps");
    }

    /**
     * Gets the maximum data rate in bits per second that a sample must have been received at in order
     * to be used in time correlation as passed through the Maximum Data Rate Filter.
     * @return the maximum accepted data rate
     */
    public int getDataRateFilterMaxRateBps() {
        return timeCorrelationConfig.getConfig().getInt("filter.dataRate.maxDataRateBps");
    }

    /**
     * Gets the maximum allowed variance from the delta between the first two samples and the deltas 
     * between all consecutive sample pairs that are allowed to pass the ERT Filter. Example: If the delta between
     * sample 0 and sample 1 is x seconds, all subsequent deltas must be (x ± maxDeltaVarianceSec) seconds.
     * @return the allowed variance of inter-sample deltas of ERT from the first ERT delta in seconds
     */
    public double getErtFilterMaxDeltaVarianceSec() {
        return timeCorrelationConfig.getConfig().getDouble("filter.ert.maxDeltaVarianceSec");
    }

    /**
     * Gets the maximum allowed variance from the delta between the first two samples and the deltas 
     * between all consecutive sample pairs that are allowed to pass the SCLK Filter. Example: If the delta between
     * sample 0 and sample 1 is x seconds, all subsequent deltas must be (x ± maxDeltaVarianceSec) seconds.
     * @return the allowed variance of inter-sample deltas of SCLK from the first SCLK delta in seconds
     */
    public double getSclkFilterMaxDeltaVarianceSec() {
        return timeCorrelationConfig.getConfig().getDouble("filter.sclk.maxDeltaVarianceSec");
    }

    /**
     * Gets the set of ground station IDs from which data may be received for time correlation.
     * @return the list of ground station IDs
     */
    public String[] getGroundStationFilterPathIds() {
        return timeCorrelationConfig.getConfig().getStringArray("filter.groundStation.pathIds");
    }

    /**
     * Gets the location of the output Raw Telemetry Table.
     * @return the path to the output Raw Telemetry Table
     * @throws MmtcException if the location cannot be accessed
     */
    public URI getRawTelemetryTableUri() throws MmtcException {
        try {
            return new URI(timeCorrelationConfig.getConfig().getString("table.rawTelemetryTable.uri"));
        } catch (URISyntaxException ex) {
            throw new MmtcException("Unable to parse URI", ex);
        }
    }

    /**
     * Indicates whether the VCID filter is enabled. Note that this method does not throw if the VCID filter is missing
     * from the configuration file altogether.
     *
     * @return true if the VCID filter is enabled in the configuration file, false
     *         otherwise (including if an exception occurs while reading the
     *         configuration)
     */
    public boolean isVcidFilterEnabled() {
        try {
            return timeCorrelationConfig.getConfig().getBoolean("filter.vcid.enabled");
        }
        catch (Exception ex) {
            return false;
        }
    }

    /**
     * Gets the valid VCID groups.
     *
     * @return the groups of valid VCIDs
     * @throws MmtcException if the groups are not specified in the
     *                                  configuration file, the value is empty, or the value contains
     *                                  any values that are non-integers
     */
    public Collection<Set<Integer>> getVcidFilterValidVcidGroups() throws MmtcException {
        return parseVcidGroups(timeCorrelationConfig, "filter.vcid.validVcidGroups");
    }

    protected static Collection<Set<Integer>> parseVcidGroups(TimeCorrelationConfig timeCorrelationConfig, String configKey) throws MmtcException {
        final String vcidGroups;
        try {
            vcidGroups = timeCorrelationConfig.getConfig().getString(configKey).trim();
        } catch (NoSuchElementException e) {
            throw new MmtcException("Missing configuration key: " + configKey);
        }

        try {
            return parseVcidGroups(configKey, vcidGroups);
        } catch (NumberFormatException e) {
            throw new MmtcException(String.format("Configuration key %s must have a value that is non-empty and set to a semicolon-separated list of non-empty comma-separated lists of integers, e.g.: `1, 2; 3, 4`", configKey));
        }
    }

    /**
     * Parses VCID groups from the list of lists contained in the configuration file.  The expected format of the VCID
     * groups is a semicolon-separated list of comma-separated lists, e.g.: "0, 6, 7; 5" which parses to one set containing
     * 0, 6, and 7; and a second set containing only 5.
     *
     * @param configKey the config key
     * @param configValue the config value, formatted as described above
     * @return a collection of sets, each set representing a valid combination of VCIDs that can be used for time correlation
     * @throws MmtcException if the format assumptions are violated
     */
    protected static Collection<Set<Integer>> parseVcidGroups(String configKey, String configValue) throws MmtcException {
        final List<Set<Integer>> ret = new ArrayList<>();

        final String formatError = String.format("Configuration key %s must have a value that is non-empty and set to a semicolon-separated list of non-empty comma-separated lists of integers, e.g.: `1 \\, 2 ; 3 \\, 4`", configKey);

        configValue = configValue.trim();

        if (configValue.isEmpty()) {
            throw new MmtcException(formatError);
        }

        for (String group : configValue.split(";")) {
            group = group.trim();
            Set<Integer> parsedVcidGroup = Arrays.stream(group.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());

            if (parsedVcidGroup.isEmpty()) {
                throw new MmtcException(formatError);
            }

            ret.add(parsedVcidGroup);
        }

        if (ret.isEmpty()) {
            throw new MmtcException(formatError);
        }

        return ret;
    }

    /**
     * Gets the location of the output RunHistoryFile
     * @return the path to the input and output RunHistoryFile
     * @throws MmtcException if the location cannot be accessed
     */
    public URI getRunHistoryFileUri() throws MmtcException {
        try {
            return new URI(timeCorrelationConfig.getConfig().getString("table.runHistoryFile.uri"));
        }
        catch (URISyntaxException ex) {
            throw new MmtcException("Unable to parse URI", ex);
        }
    }

    /**
     * Gets the location of the output Summary Table.
     * @return the path to the input and output Summary Table
     * @throws MmtcException if the location cannot be accessed
     */
    public URI getSummaryTableUri() throws MmtcException {
        try {
            return new URI(timeCorrelationConfig.getConfig().getString("table.summaryTable.uri"));
        }
        catch (URISyntaxException ex) {
            throw new MmtcException("Unable to parse URI", ex);
        }
    }

    /**
     * Gets the location of the output Time History File.
     * @return the path to the output Time History File
     * @throws MmtcException if the location cannot be accessed
     */
    public URI getTimeHistoryFileUri() throws MmtcException {
        try {
            return new URI(timeCorrelationConfig.getConfig().getString("table.timeHistoryFile.uri"));
        }
        catch (URISyntaxException ex) {
            throw new MmtcException("Unable to parse URI", ex);
        }
    }

    public List<String> getTimeHistoryFileExcludeColumns() {
        return Arrays.asList(timeCorrelationConfig.getConfig().getStringArray("table.timeHistoryFile.excludeColumns"));
    }

    /**
     * Get the path to the input SCLK kernel, which can be one of the
     * following:
     *<p>
     *  (1) the latest or most recent kernel as determined by the version
     *      number in the filename, or
     *  (2) the path to an SCLK kernel explicitly given by the user
     *<p>
     *  In the case of (1), returns the full file specification of the latest
     *  SCLK kernel in the indicated directory. Assumes that the
     *  lexicographically greatest filename is the latest. Filters files in the
     *  directory assuming that the SCLK kernel filenames begin with the the
     *  SCLK kernel basename and ends with the SCLK kernel file suffix (usually
     *  .tsc) provided in configuration parameters.
     *<p>
     *  In the case of (2), the path given in the inputPathOverride configuration parameter is used.
     *  This is intended for use in test environments only or in anomalous circumstances. It can cause
     *  problems with filename duplication if used operationally.
     *
     * @return the path to the input SCLK kernel
     * @throws MmtcException if the SCLK path is invalid
     */
    public Path getSclkKernelPath() throws MmtcException {
        Path result;

        // If the SCLK override is specified by this configuration parameter, use that SCLK kernel.
        // Otherwise, find the SCLK in the directory with the latest version number and use it.
        if (timeCorrelationConfig.getConfig().containsKey("spice.kernel.sclk.inputPathOverride")) {
            try {
                result = Paths.get(timeCorrelationConfig.getConfig().getString(
                        "spice.kernel.sclk.inputPathOverride"));
            }
            catch (InvalidPathException ex) {
                throw new MmtcException("Invalid input SCLK kernel override path");
            }
        }
        else {
            final String sclkBaseName = timeCorrelationConfig.getConfig().getString(
                    "spice.kernel.sclk.baseName");
            final Path sclkDir = Paths.get(timeCorrelationConfig.getConfig().getString(
                    "spice.kernel.sclk.kerneldir"));
            final String namePattern = "glob:**/" + sclkBaseName + "*" + SclkKernel.FILE_SUFFIX;
            final PathMatcher filter = sclkDir.getFileSystem().getPathMatcher(namePattern);
            List<Path> sclkKernelPaths;

            try (final Stream<Path> stream = Files.list(sclkDir)) {
                sclkKernelPaths = stream.filter(filter::matches).sorted().collect(Collectors.toList());
            } catch (IOException ex) {
                throw new MmtcException("Unable to read SCLK kernel directory: " + sclkDir, ex);
            }


            if (sclkKernelPaths.size() > 0) {
                result = sclkKernelPaths.get(sclkKernelPaths.size() - 1);
            } else {
                throw new MmtcException("Unable to find seed SCLK kernel in " + sclkDir);
            }
        }

        return result;
    }

    /**
     * Gets the directory to write the output SCLK Kernel to.
     * @return the directory to write the SCLK Kernel to
     */
    public Path getSclkKernelOutputDir() {
        return Paths.get(timeCorrelationConfig.getConfig().getString("spice.kernel.sclk.kerneldir"));
    }

    /**
     * Gets the SCLK Kernel basename, i.e., the first and static part of the SCLK Kernel filename.
     * For example, given "europaclipper_00000.tsc", "europaclipper" is the basename.
     *
     * @return the SCLK Kernel basename
     */
    public String getSclkKernelBasename() {
        return timeCorrelationConfig.getConfig().getString("spice.kernel.sclk.baseName");
    }

    /**
     * Gets the SCLK Kernel name separator. The separator is the character between the basename and the version number
     * of the SCLK kernel. For example, given "europaclipper_00000.tsc", "_" (underscore) is the separator. Underscore
     * is the default.
     *
     * @return the name separator
     */
    public String getSclkKernelSeparator() {
        return timeCorrelationConfig.getConfig().getString("spice.kernel.sclk.separator", "_");
    }

    public boolean generateUniqueKernelCounters() {
        if (containsNonEmptyKey("spice.kernel.sclk.uniqueKernelCounters")) {
            return timeCorrelationConfig.getConfig().getBoolean("spice.kernel.sclk.uniqueKernelCounters");
        } else {
            return true;
        }
    }

    /**
     * Gets the parameter that specifies if an SCLK/SCET file is to be created. Default is to create it.
     * @return the parameter
     */
    public boolean createSclkScetFile() {
        return timeCorrelationConfig.getConfig().getBoolean("product.sclkScetFile.create", true);
    }

    /**
     * Gets the directory to write the output SCLK/SCET file to.
     * @return the directory to write the SCLK/SCET to
     */
    public Path getSclkScetOutputDir() {
        return Paths.get(timeCorrelationConfig.getConfig().getString("product.sclkScetFile.dir"));
    }

    /**
     * Gets the SCLK/SCET file basename, i.e., the first and static part of the SCLK Kernel filename.
     * For example, given "europaclipper_00000.coeff", "europaclipper" is the basename.
     *
     * @return the SCLK/SCET file basename
     */
    public String getSclkScetFileBasename() {
        return timeCorrelationConfig.getConfig().getString("product.sclkScetFile.baseName");
    }

    /**
     * Gets the SCLK/SCET file name separator. The separator is the character between the basename and the version number
     * of the file. For example, given "europaclipper_00000.coeff", "_" (underscore) is the separator. Underscore is
     * the default.
     *
     * @return the name separator
     */
    public String getSclkScetFileSeparator() {
        return timeCorrelationConfig.getConfig().getString("product.sclkScetFile.separator", "_");
    }

    /**
     * Get the SCLK/SCET file type suffix.
     * For example, given "europaclipper_00000.coeff", ".coeff" is the suffix.
     * NOTE: Unlike the SCLK Kernel, there is no standard or convention as to what this should be.
     *
     * @return the SCLK/SCET file type suffix
     */
    public String getSclkScetFileSuffix() {
        return timeCorrelationConfig.getConfig().getString("product.sclkScetFile.suffix");
    }

    /**
     * Gets the number of days to look back into the SCLK kernel from the current time for
     * purposes of computing the predicted clock change rate. The number of days may contain
     * a fractional part.
     *
     * @return the number of days to look back
     */
    public Double getPredictedClkRateLookBackDays() {
        return timeCorrelationConfig.getConfig().getDouble("compute.tdtG.rate.predicted.lookBackDays");
    }

    /**
     * Gets the maximum number of hours to look back into the SCLK kernel from the current time for
     * purposes of computing the predicted clock change rate. This specifies how far back is too far
     * in finding the previous time correlation in computing Predicted CLKRATE. This value is read-in
     * from the config parameters as a floating point type representing days, multiplied by 24 to produce
     * hours, and then the fractional part truncated to the integer whole hour.
     *
     * @return the maximum number of hours to look back
     */
    public Integer getMaxPredictedClkRateLookBackHours() {
        Float maxLookBack = timeCorrelationConfig.getConfig().getFloat("compute.tdtG.rate.predicted.maxLookBackDays") * 24;
        return new Integer(maxLookBack.intValue());
    }

    /**
     * Gets the maximum error, in milliseconds, that can be computed for TDT(S) before indicating a warning in
     * the TimeHistoryFile
     * @return the error threshold for TDT(S)
     */
    public double getTdtSErrorWarningThresholdMs() {
        return timeCorrelationConfig.getConfig().getDouble("compute.tdtS.threshold.errorMsecWarning");
    }

    /**
     * Gets the number of digits to which the SCET fraction of second is to be written in an SCLK/SCET file.
     * @return the number decimal digits to write
     */
    public Integer getSclkScetScetUtcPrecision() {
        return timeCorrelationConfig.getConfig().getInt("product.sclkScetFile.scetUtcPrecision");
    }

    /**
     * Gets the number of days past the correlation time in SCET (UTC) that the product should be valid/'applicable' for,
     * which is written to the header of the file.  If the key is not specified, returns 0
     *
     * @return the number of days post-correlation that the file should be valid, or 0 if not specified
     */
    public Integer getSclkScetApplicableDurationDays() {
        return timeCorrelationConfig.getConfig().getInt("product.sclkScetFile.applicableDurationDays", 0);
    }


    public enum SclkScetFileLeapSecondSclkRate {
        ONE,
        PRIOR_RATE,
    }

    /**
     * Gets the mode that instructs MMTC how to set the SCLKRATE in SCLK-SCET files.  The two options are 'PRIOR_RATE',
     * which reuses the SCLKRATE from the record just before the leap second entry pair for the second leap second entry,
     * or 'ONE' which simply assigns a 1.0 rate to the second leap second entry.
     *
     * @return the SCLK-SCET leap second clock change rate mode, or PRIOR_RATE if not specified
     */
    public SclkScetFileLeapSecondSclkRate getSclkScetLeapSecondRateMode() {
        final String mode = timeCorrelationConfig.getConfig().getString("product.sclkScetFile.leapSecondSclkRateMode", "PRIOR_RATE");
        return SclkScetFileLeapSecondSclkRate.valueOf(mode);

    }

    /**
     * Gets the number of digits to which the SCET fraction of second is to be written in the Time History File.
     * @return the number decimal digits to write
     */
    public Integer getTimeHistoryFileScetUtcPrecision() {
        return timeCorrelationConfig.getConfig().getInt("table.timeHistoryFile.scetUtcPrecision");
    }

    /**
     * Gets the full name of the spacecraft.
     * @return the name of the spacecraft as a string
     */
    public String getSpacecraftName() {
        return timeCorrelationConfig.getConfig().getString("spacecraftName");
    }

    /**
     * Gets the SANA-assigned Spacecraft ID (SCID).
     * @return the spacecraft ID
     */
    public int getSpacecraftId() {
        return timeCorrelationConfig.getConfig().getInt("spacecraft.id");
    }

    /**
     * Gets the spacecraft internal time delay. This is the number of seconds (usually a fraction of a second) delay in
     * the spacecraft systems that affects a frame radiation SCLK.
     *
     * @return the spacecraft time delay in seconds
     */
    public double getSpacecraftTimeDelaySec() {
        return timeCorrelationConfig.getConfig().getDouble("spacecraft.timeDelaySec");
    }

    /**
     * Gets the Bit Delay-Bit Rate Error. If the spacecraft timestamps the
     * transmission starting at a specific part of the frame, but the ground station
     * timestamps the receipt starting at a different part of the frame, then the
     * correlated times will be offset by a "Bit Rate Error" time delay. This delay
     * can be computed from the downlink rate and the bit offset between the parts
     * of the frame that the spacecraft and the ground station use.
     *
     * This method gets the configuration value that represents that bit offset.
     *
     * @return the bit offset between the parts of the frame that the spacecraft and
     *         the ground station use
     */
    public double getFrameErtBitOffsetError() {
        return timeCorrelationConfig.getConfig().getDouble("spacecraft.frameErtBitOffsetError");
    }

    /**
     * Gets the mission ID. This is used in the SCLK/SCET file and is often, but not always, the same as the
     * spacecraft name.
     *
     * @return the mission ID used in the SCLK/SCET file
     */
    public int getMissionId() {
        return timeCorrelationConfig.getConfig().getInt("missionId");
    }

    /**
     * Gets the value for the DATA_SET_ID field to be included in the header of the SCLK/SCET file.
     * @return the dataset ID used in the SCLK/SCET file
     */
    public String getDataSetId() {
        return timeCorrelationConfig.getConfig().getString("product.sclkScetFile.datasetId");
    }

    /**
     * Gets the value for the PRODUCER_ID field to be included in the header of the SCLK/SCET file.
     * @return the producer ID used in the SCLK/SCET file
     */
    public String getProducerId() {
        return timeCorrelationConfig.getConfig().getString("product.sclkScetFile.producerId");
    }

    /**
     * Indicates if an optional Uplink Command File is to be created. This can be specified in either the command line
     * or in configuration parameters. Command line overrides the configuration parameter.
     *
     * @return true if an Uplink Command File is to be created
     */
    public boolean createUplinkCmdFile() {
        if (cmdLineConfig.isGenerateCmdFile()) {
            return true;
        } else {
            return timeCorrelationConfig.getConfig().getBoolean("product.uplinkCmdFile.create", false);
        }
    }

    /**
     * Gets the directory to which the optional Uplink Command File is to be written.
     * @return the Uplink Command File output directory
     */
    public String getUplinkCmdFileDir() {
        return timeCorrelationConfig.getConfig().getString("product.uplinkCmdFile.outputDir");
    }

    /**
     * Gets the Uplink Command file basename, i.e., the first and static part of the filename.
     * For example, given "uplinkCmd1577130458.csv", "uplinkCmd" is the basename.
     *
     * @return the SCLK/SCET file basename
     */
    public String getUplinkCmdFileBasename() {
        return timeCorrelationConfig.getConfig().getString("product.uplinkCmdFile.baseName");
    }

    // Config parameters associated with the time correlation (TK) packet.

    /**
     * Returns the time window in seconds surrounding a TK Oscillator Temperature telemetry point that can be
     * included in a query. It is the seconds +/- precision of a SCET query value in which the query can be found.
     * Values for the telemetry point outside of this window will not match.
     *
     * @return the time window in seconds
     */
    public int getTkOscTempWindowSec()  {
        return timeCorrelationConfig.getConfig().getInt("telemetry.tkOscTempWindowSec");
    }

    /**
     * Returns the number of seconds after the SCET of a selected telemetry point containing one
     * of the static time correlation parameters in which the data item is considered valid. The desired
     * TLM point may not be available at exactly the ERT queried. This allows a query for the telemetry
     * point at ERT +/- [this value].
     *
     * @return the time in seconds within which a TK parameter TLM point may be queried.
     */
    public int getTkParmWindowSec() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.tkParmWindowSec");
    }

    /**
     * Gets the full file specification (director/name) of the input Ground Stations Map file.
     * @return the ground station map file path
     */
    public String getGroundStationMapPath() {
        return timeCorrelationConfig.getConfig().getString("groundStationMap.path");
    }

    /**
     * Gets the full file specification (director/name) of the input SCLK Partition Map file.
     * @return the SCLK partition map file path
     */
    public String getSclkPartitionMapPath() {
        return timeCorrelationConfig.getConfig().getString("sclkPartitionMap.path");
    }

    public List<String> getValidOscillatorIds() {
        if (! containsNonEmptyKey("spacecraft.oscillatorIds")) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(getStringArray("spacecraft.oscillatorIds"));
        }
    }

    /**
     * Returns the number of ticks per second measured by the onboard Spacecraft Clock (SCLK). This is
     * also called the "subseconds modulus", "SCLK modulus", or the "tick rate". In most missions,
     * this value is based on the resolution of the GNC clock and is given in the SCLK01_MODULI_ss
     * field of the input SCLK kernel; however, in some cases (e.g., Europa Clipper) it is added to the
     * time correlation packets by another subsystem (e.g., the radio) after they are created using a
     * different value. For purposes of computing TF Offset, the subseconds modulus must be that matches
     * the SCLK clock used for time correlation.
     *
     * @return the subseconds modulus in ticks per second
     */
    public Integer getSclkModulusOverride() {
        // If this parameter is not found or cannot be retrieved, handle the exception and return a null value.
        try {
            return timeCorrelationConfig.getConfig().getInt("spacecraft.sclkModulusOverride");
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Gets the pattern that is used to parse ERTs from query metadata and to write ERT to the RawTlmTable in calendar
     * string date/time form (e.g., ISO DOY format: "yyyy-DDD'T'HH:mm:ss.SSSSSS").
     *
     * @return the calendar string format from configuration parameters
     */
    public String getRawTlmTableDateTimePattern() {
        return timeCorrelationConfig.getConfig().getString("table.rawTelemetryTable.dateTimePattern");
    }

    public boolean getRawTlmTableReadDownlinkDataRate() {
        return timeCorrelationConfig.getConfig().getBoolean("telemetry.source.plugin.rawTlmTable.readDownlinkDataRate");
    }

    public String getString(String key) {
        return timeCorrelationConfig.getConfig().getString(key);
    }

    public Boolean getBoolean(String key) {
        return timeCorrelationConfig.getConfig().getBoolean(key);
    }

    public int getInt(String key) {
        return timeCorrelationConfig.getConfig().getInt(key);
    }

    public String[] getStringArray(String key) {
        return timeCorrelationConfig.getConfig().getStringArray(key);
    }

    /**
     * Returns the number of ticks per second measured by the onboard Spacecraft Clock (SCLK). This is
     * also called the "subseconds modulus", "SCLK modulus", or the "tick rate". In most missions,
     * this value is based on the resolution of the GNC clock and is given in the SCLK01_MODULI_ss
     * field of the input SCLK kernel; however, in some cases (e.g., Europa Clipper) it is added to the
     * time correlation packets by another subsystem (e.g., the radio) after they are created using a
     * different value. For purposes of computing TF Offset, the subseconds modulus must be that which
     * matches the SCLK clock used for time correlation.
     *
     * The default is to read the subseconds modulus from the SCLK Kernel. However, if the optional
     * spacecraft.sclkModulusOverride configuration parameter is provided, it reads it from the
     * configuration parameter.
     *
     * @return the subseconds modulus in ticks per second for the configured NAIF spacecraft ID
     * @throws TimeConvertException if the value could not be obtained from the SCLK Kernel
     */
    public Integer getTkSclkFineTickModulus() throws TimeConvertException {
        int tk_sclk_fine_tick_modulus = getSclkModulusOverride();

        if (tk_sclk_fine_tick_modulus == -1) {
            tk_sclk_fine_tick_modulus = TimeConvert.getSclkKernelTickRate(getNaifSpacecraftId());
            logger.info("Read TF Offset SCLK subseconds modulus of " + tk_sclk_fine_tick_modulus + " from SCLK Kernel.");
        } else {
            logger.info("Read TF Offset SCLK subseconds modulus of " + tk_sclk_fine_tick_modulus + " from configuration parameters.");
        }

        return tk_sclk_fine_tick_modulus;
    }

    public TelemetrySelectionStrategy.SampleSetBuildingStrategy getSampleSetBuildingStrategy() {
        return TelemetrySelectionStrategy.SampleSetBuildingStrategy.valueOf(
                timeCorrelationConfig.getConfig().getString("telemetry.sampleSetBuildingStrategy")
        );
    }

    public int getSamplingSampleSetBuildingStrategyQueryWidthMinutes() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.sampleSetBuildingStrategy.sampling.queryWidthMinutes");
    }

    public int getSamplingSampleSetBuildingStrategySamplingRateMinutes() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.sampleSetBuildingStrategy.sampling.samplingRateMinutes");
    }

    /**
     * Method used for up-front validation of the presence of all required keys in TimeCorrelationAppConfig.xml. This is
     * intended to be used with the standard base config found at {$TK_CONFIG_PATH}/examples/TimeCorrelationConfigProperties-base.xml,
     * but any valid XML config can technically be used.
     * @param baseConfigPath Path to the baseline config file that the active config will be compared against
     * @return An ArrayList of key names missing from the active config (or an empty list if all required keys are found)
     * @throws MmtcException if the baseline config cannot be parsed.
     */
    public ArrayList<String> validateRequiredConfigKeys(String baseConfigPath) throws MmtcException {
        ArrayList<String> missingKeys = new ArrayList<>();
        FileBasedConfiguration activeConf = timeCorrelationConfig.getConfig();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MmtcException("Failed to initialize a new DocumentBuilder while attempting to validate the configuration file.");
        }

        // Parse TimeCorrelationConfigProperties-base.xml
        ClassLoader classLoader = TimeCorrelationAppConfig.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(baseConfigPath)) {
            Document document = builder.parse(stream);
            document.getDocumentElement().normalize();
            NodeList nodeList = document.getElementsByTagName("entry");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    if (!activeConf.containsKey(element.getAttribute("key"))) {
                        missingKeys.add(element.getAttribute("key"));
                    }
                }
            }
        } catch (SAXException | IOException e) {
            throw new MmtcException(String.format("Failed to validate the configuration file against %s,", baseConfigPath), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return missingKeys;
    }
}
