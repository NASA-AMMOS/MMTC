package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.jhuapl.sd.sig.mmtc.products.definition.*;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.CachingTelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionStrategy;

import edu.jhuapl.sd.sig.mmtc.util.FileUtils;
import edu.jhuapl.sd.sig.mmtc.util.IsolatingUrlClassLoader;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.FileBasedConfiguration;
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

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

/**
 * A class assisting with loading and providing access to values in file-based configuration. These include the
 * parameters read from the TimeCorrelationConfigProperties.xml file, Ground Station Map associations,
 * and the SCLK Partition Map associations.
 *
 * Functions in this class provide access to each item in the TimeCorrelationConfigProperties.xml
 * configuration parameters file.
 */
public abstract class MmtcConfig {
    private static final String BASE_CONFIG_FILENAME = "TimeCorrelationConfigProperties-base.xml";

    private static final Set<String> BUILT_IN_TLM_SOURCES = new HashSet<>(Collections.singletonList("rawTlmTable"));

    public static final List<ClockChangeRateMode> CLOCK_CHANGE_RATE_ASSIGN_MODES = Arrays.asList(ClockChangeRateMode.ASSIGN, ClockChangeRateMode.ASSIGN_KEY);
    public static final ClockChangeRateMode DEFAULT_CLOCK_CHANGE_RATE_MODE = ClockChangeRateMode.COMPUTE_INTERPOLATE;

    protected final Path mmtcHome;
    protected final TimeCorrelationConfig timeCorrelationConfig;
    protected final List<OutputProductDefinition<?>> allProductDefs;

    protected GroundStationMap groundStationMap;
    protected SclkPartitionMap sclkPartitionMap;

    private static final Logger logger = LogManager.getLogger();

    public MmtcConfig() throws Exception {
        logger.debug("Loading configuration");

        this.mmtcHome = Paths.get(System.getenv("MMTC_HOME")).toAbsolutePath();
        this.timeCorrelationConfig = new TimeCorrelationXmlPropertiesConfig();

        if (! timeCorrelationConfig.load()) {
            throw new MmtcException("Error loading " + timeCorrelationConfig.getPath());
        }

        this.groundStationMap = new GroundStationMap(getGroundStationMapPath());
        if (!groundStationMap.load()) {
            throw new MmtcException("Error loading " + groundStationMap.getPath());
        }
        logger.info("Loaded ground stations map " + groundStationMap.getPath());

        this.sclkPartitionMap = new SclkPartitionMap(getSclkPartitionMapPath());
        if (!sclkPartitionMap.load()) {
            throw new MmtcException("Error loading " + sclkPartitionMap.getPath());
        }
        logger.info("Loaded SCLK clock partition map " + sclkPartitionMap.getPath());

        this.allProductDefs = Collections.unmodifiableList(constructAllOutputProductDefinitions());
    }

    public MmtcConfig(MmtcConfig config) {
        this.mmtcHome = config.mmtcHome;
        this.timeCorrelationConfig = config.timeCorrelationConfig;
        this.groundStationMap = config.groundStationMap;
        this.sclkPartitionMap = config.sclkPartitionMap;
        this.allProductDefs = config.allProductDefs;
    }

    public Path getConfigFilepath() {
        return this.timeCorrelationConfig.getPath();
    }

    public Path getMmtcHome() {
        return mmtcHome;
    }

    public List<OutputProductDefinition<?>> getAllOutputProductDefs() {
        return this.allProductDefs;
    }

    public OutputProductDefinition<?> getOutputProductDefByName(String name) throws MmtcException {
        return this.allProductDefs.stream()
                .filter(def -> def.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new MmtcException("No such product found: " + name));
    }

    public EntireFileOutputProductDefinition getEntireFileProductDefByName(String name) throws MmtcException {
        Optional<OutputProductDefinition<?>> maybeDef = this.allProductDefs.stream()
                .filter(def -> def instanceof EntireFileOutputProductDefinition)
                .filter(def -> def.getName().equals(name))
                .findFirst();

        return (EntireFileOutputProductDefinition) maybeDef.orElseThrow(() -> new MmtcException("No such product found: " + name));
    }

    public AppendedFileOutputProductDefinition getAppendedOutputProductDefByName(String name) throws MmtcException {
        Optional<OutputProductDefinition<?>> maybeDef = this.allProductDefs.stream()
                .filter(def -> def instanceof AppendedFileOutputProductDefinition)
                .filter(def -> def.getName().equals(name))
                .findFirst();

        return (AppendedFileOutputProductDefinition) maybeDef.orElseThrow(() -> new MmtcException("No such product found: " + name));
    }

    private List<OutputProductDefinition<?>> constructAllOutputProductDefinitions() throws MmtcException, IOException {
        final List<OutputProductDefinition<?>> productDefs = new ArrayList<>();

        // first, load all built-in product defs
        {
            final OutputProductDefinitionFactory builtInProdDefFactory = new BuiltInOutputProductDefinitionFactory();
            for (String builtInProductType : builtInProdDefFactory.getApplicableTypes()){
                final OutputProductDefinition<?> def = builtInProdDefFactory.create(builtInProductType, null, null);
                def.setIsBuiltIn(true);
                productDefs.add(def);
            }
        }

        // next, load all plugin-provided product defs
        final List<PluginProvidedProductConfig> pluginProvidedProductConfigs = new ArrayList<>();
        for (String productName : getStringArray("product.plugin.outputProductNames")) {
            final PluginProvidedProductConfig pluginProductConfig = PluginProvidedProductConfig.createFrom(this, productName);

            if (pluginProductConfig.enabled) {
                pluginProvidedProductConfigs.add(pluginProductConfig);
            }
        }

        productDefs.addAll(loadAllPluginProvidedOutputProductDefinitionFor(pluginProvidedProductConfigs));

        // ensure product definitions provide unique names
        if (productDefs.stream().map(def -> def.getName()).collect(Collectors.toSet()).size() != productDefs.size()) {
            throw new IllegalStateException("Please check your loaded configuration and/or plugins to ensure all output products have unique names.  Loaded names are: " + productDefs.stream().map(def -> def.getName()).collect(Collectors.toList()));
        }

        // lastly, validate that they're all of either of the two supported types (constrained by the rollback feature)
        for (OutputProductDefinition<?> def : productDefs) {
            if ((! (def instanceof EntireFileOutputProductDefinition)) && (! (def instanceof AppendedFileOutputProductDefinition))) {
                throw new IllegalStateException("Product def " + def.getName() + " must either be subclassed from EntireFileProductDefinition or AppendedFileOutputProductDefinition");
            }
        }

        return productDefs;
    }

    private static List<OutputProductDefinition<?>> loadAllPluginProvidedOutputProductDefinitionFor(List<PluginProvidedProductConfig> pluginProvidedProductConfigs) throws MmtcException, IOException {
        final Map<PluginProvidedProductConfig, Path> perConfigPluginJar = new HashMap<>();
        final Map<Path, ServiceLoader<OutputProductDefinitionFactory>> perJarServiceLoaders = new HashMap<>();

        // iterate through defined plugin products, using ServiceLoader to load all OutputProductDefinitionFactorys from each referenced jar once
        for (PluginProvidedProductConfig pluginProdConf : pluginProvidedProductConfigs) {
            final Path pluginJarPath = FileUtils.findUniqueJarFileWithinDir(pluginProdConf.pluginDir, pluginProdConf.pluginJarPrefix);
            perConfigPluginJar.put(pluginProdConf, pluginJarPath);

            if (! perJarServiceLoaders.containsKey(pluginJarPath)) {
                logger.info("Loading output product plugin implementations from {}", pluginJarPath);

                final URL[] urls = { pluginJarPath.toAbsolutePath().toUri().toURL() };

                // not closing this classloader here, because if we did, further classes won't be able to be loaded from the jar file
                URLClassLoader cl = new IsolatingUrlClassLoader(urls);
                final ServiceLoader<OutputProductDefinitionFactory> outputProductDefFactoryLoader = ServiceLoader.load(OutputProductDefinitionFactory.class, cl);
                perJarServiceLoaders.put(pluginJarPath, outputProductDefFactoryLoader);
            }
        }

        // check that factories can't produce defs of the same type; ensure the factories specify completely separate types
        {
            final Set<String> allProvidedTypes = new HashSet<>();
            for (Map.Entry<Path, ServiceLoader<OutputProductDefinitionFactory>> entry : perJarServiceLoaders.entrySet()) {
                ServiceLoader<OutputProductDefinitionFactory> sl = entry.getValue();
                for (OutputProductDefinitionFactory fact : sl) {

                    if (fact instanceof BuiltInOutputProductDefinitionFactory) {
                        // this can be picked up due to IsolatingUrlClassLoader's delegation behavior; skip it here
                        continue;
                    }

                    for (String t : fact.getApplicableTypes()) {
                        if (allProvidedTypes.contains(t) || BuiltInOutputProductDefinitionFactory.BUILT_IN_PRODUCT_TYPES.contains(t)) {
                            throw new IllegalStateException("More than one of a loaded plugin or MMTC core provides a product of type: " + t);
                        }
                        allProvidedTypes.add(t);
                    }
                }
            }
        }

        final List<OutputProductDefinition<?>> productDefs = new ArrayList<>();
        for (PluginProvidedProductConfig pluginProdConf : pluginProvidedProductConfigs) {
            final Path jarWithImpl = perConfigPluginJar.get(pluginProdConf);
            final ServiceLoader<OutputProductDefinitionFactory> serviceLoader = perJarServiceLoaders.get(jarWithImpl);
            for (OutputProductDefinitionFactory prodDefFactory : serviceLoader) {
                if (prodDefFactory.getApplicableTypes().contains(pluginProdConf.outputProductType)) {
                    final OutputProductDefinition<?> def = prodDefFactory.create(
                            pluginProdConf.outputProductType,
                            pluginProdConf.outputProductName,
                            pluginProdConf.config
                    );
                    def.setIsBuiltIn(false);
                    productDefs.add(def);

                    logger.info(String.format(
                            "Initialized output product configuration for %s (of type %s) from %s",
                            pluginProdConf.outputProductName,
                            pluginProdConf.outputProductType,
                            jarWithImpl
                    ));
                }
            }
        }

        return productDefs;
    }

    protected String getEnsureNotEmpty(String key) throws MmtcException {
        if (! containsKey(key)) {
            throw new MmtcException("No such key: " + key);
        }

        final String val = getString(key);

        if (val.trim().isEmpty()) {
            throw new MmtcException("Empty value for key: " + key);
        }

        return val;
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
     * Use Java ServiceLoader to find and load a telemetry source
     *
     * @return the initialized telemetry source
     * @throws Exception if the configured plugin can not be found or initialized
     */
    protected TelemetrySource initTlmSource() throws Exception {
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
            final Path pluginJarPath = FileUtils.findUniqueJarFileWithinDir(getTelemetrySourcePluginDirectory(), getTelemetrySourcePluginJarPrefix());

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

        if (isTelemetrySourceCachingEnabled()) {
            logger.info("Loaded telemetry source {} with caching enabled", tlmSource.getName());
            return new CachingTelemetrySource(getTelemetrySourceCacheLocation(), tlmSource);
        } else {
            logger.info("Loaded telemetry source {}", tlmSource.getName());
            return tlmSource;
        }
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
     * Get whether to insert an additional 'smoothing' record to the SCLK kernel and SCLK-SCET files.  If unset,
     * defaults to false
     * @return true if this feature is enabled, false otherwise
     */
    public boolean isAdditionalSmoothingCorrelationRecordInsertionEnabled() {
        return timeCorrelationConfig.getConfig().getBoolean("compute.additionalSmoothingCorrelationRecordInsertion.enabled", false);
    }

    /**
     * Get whether to insert an additional 'smoothing' record to the SCLK kernel and SCLK-SCET files.  If unset,
     * defaults to false
     * @return true if this feature is enabled, false otherwise
     */
    public int getAdditionalSmoothingCorrelationRecordInsertionCoarseSclkTickDuration() throws MmtcException {
        final int val = timeCorrelationConfig.getConfig().getInt("compute.additionalSmoothingCorrelationRecordInsertion.coarseSclkTickDuration", 100);
        if (val < 1) {
            throw new MmtcException("The config key 'compute.additionalSmoothingCorrelationRecordInsertion.coarseSclkTickDuration' must have a value of 1 or greater.");
        }
        return val;
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
        // the fact that this is a LinkedHashMap is important, as it means iteration over the map will proceed in insertion-order
        Map<String, String> kernels = new LinkedHashMap<>();

        // Input metakernel
        if (timeCorrelationConfig.getConfig().containsKey("spice.kernel.mk.path")) {
            kernels.put(timeCorrelationConfig.getConfig().getString("spice.kernel.mk.path"), "mk");
        }

        // Get the path to the SCLK kernel. This will be the highest versioned SCLK kernel that matches
        // the file pattern given in the configuration parameters, unless spice.kernel.sclk.inputPathOverride
        // is set in the configuration parameters. The inputPathOverride parameter overrides the normal
        // previous SCLK kernel search.
        kernels.put(getInputSclkKernelPath().toString(), "sclk");

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

    public boolean isTelemetrySourceCachingEnabled() {
        if (containsKey("telemetry.cacheFilePath")) {
            return ! getString("telemetry.cacheFilePath").isEmpty();
        }

        return false;
    }

    public Path getTelemetrySourceCacheLocation() {
        return Paths.get(timeCorrelationConfig.getConfig().getString("telemetry.cacheFilePath")).toAbsolutePath();
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
     */
    public Path getRawTelemetryTablePath() {
        return ensureAbsolute(Paths.get(timeCorrelationConfig.getConfig().getString("table.rawTelemetryTable.path")));
    }

    public Path ensureAbsolute(Path path) {
        if (! path.isAbsolute()) {
            return mmtcHome.resolve(path);
        }
        return path;
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
     */
    public Path getRunHistoryFilePath() {
        return ensureAbsolute(Paths.get(timeCorrelationConfig.getConfig().getString("table.runHistoryFile.path")));
    }

    public boolean createTimeHistoryFile() {
        return timeCorrelationConfig.getConfig().getBoolean("table.timeHistoryFile.create", true);
    }

    /**
     * Gets the location of the output Time History File.
     * @return the path to the output Time History File
     */
    public Path getTimeHistoryFilePath() {
        return ensureAbsolute(Paths.get(timeCorrelationConfig.getConfig().getString("table.timeHistoryFile.path")));
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
    public Path getInputSclkKernelPath() throws MmtcException {
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
     * hours
     *
     * @return the maximum number of hours to look back
     */
    public double getMaxPredictedClkRateLookBackHours() {
        return timeCorrelationConfig.getConfig().getFloat("compute.tdtG.rate.predicted.maxLookBackDays") * 24.0;
    }

    /**
     * Gets the maximum error, in milliseconds, that can be computed for TDT(S) before indicating a warning in
     * the TimeHistoryFile
     * @return the error threshold for TDT(S)
     */
    public Optional<Double> getTdtSErrorWarningThresholdMs() {
        if (timeCorrelationConfig.getConfig().containsKey("compute.tdtS.threshold.errorMsecWarning")) {
            return Optional.of(timeCorrelationConfig.getConfig().getDouble("compute.tdtS.threshold.errorMsecWarning"));
        } else {
            return Optional.empty();
        }
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

    /**
     * Gets the number of decimal places/fractional digits to display when recording oscillator temperature in the
     * time history file
     * @return the integer specified in the config unless key isn't present or can't be parsed, then return safe default
     */
    public int getOscTempFractionDigits() throws MmtcException {
        final String keyName = "table.timeHistoryFile.oscTempFractionDigits";
        int numDigits = 9;
        
        try {
            if (timeCorrelationConfig.getConfig().containsKey(keyName)) {
                numDigits = timeCorrelationConfig.getConfig().getInt(keyName);
            } else {
                logger.debug("Key {} not found in TimeCorrelationConfigProperties: defaulting to value {}", keyName, numDigits);
            }
        } catch (Exception e) {
            throw new MmtcException(String.format("Failed to parse value provided in config key %s, ensure it's a valid integer.", keyName), e);
        }
        return numDigits;
    }

    /**
     * Gets the path at which to write zip archives containing backups of MMTC output products.
     * @return the path as described above
     */
    public Path getProductArchiveLocation() {
        if (containsKey("product.archive.directory")) {
            return Paths.get(getString("product.archive.directory"));
        } else {
            return getRunHistoryFilePath().getParent().resolve("output-archives");
        }
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

    public boolean isCreateUplinkCmdFile() {
        return timeCorrelationConfig.getConfig().getBoolean("product.uplinkCmdFile.create", false);
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
     * If not set, the default value is 600 seconds (10 minutes.)
     *
     * @return the time window in seconds
     */
    public int getTkOscTempWindowSec()  {
        return timeCorrelationConfig.getConfig().getInt("telemetry.tkOscTempWindowSec", 600);
    }

    /**
     * Returns the number of seconds after the SCET of a selected telemetry point containing one
     * of the static time correlation parameters in which the data item is considered valid. The desired
     * TLM point may not be available at exactly the ERT queried. This allows a query for the telemetry
     * point at ERT +/- [this value].
     *
     * If not set, the default value is 600 seconds (10 minutes.)
     *
     * @return the time in seconds within which a TK parameter TLM point may be queried.
     */
    public int getTkParmWindowSec() {
        return timeCorrelationConfig.getConfig().getInt("telemetry.tkParmWindowSec", 600);
    }

    /**
     * Gets the full file specification (directory/name) of the input Ground Stations Map file.
     * @return the ground station map file path
     */
    public Path getGroundStationMapPath() {
        return Paths.get(timeCorrelationConfig.getConfig().getString("groundStationMap.path"));
    }

    /**
     * Gets the full file specification (directory/name) of the input SCLK Partition Map file.
     * @return the SCLK partition map file path
     */
    public Path getSclkPartitionMapPath() {
        return Paths.get(timeCorrelationConfig.getConfig().getString("sclkPartitionMap.path"));
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
        // If this parameter is not found or cannot be retrieved, handle the exception and return a negative value.
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

    public List<String> getKeysWithPrefix(String keyPrefix) {
        final List<String> keys = new ArrayList<>();

        Iterator<String> keyIterator = timeCorrelationConfig.getConfig().getKeys(keyPrefix);

        while (keyIterator.hasNext()) {
            keys.add(keyIterator.next());
        }

        return keys;
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

    public enum ClockChangeRateMode {
        COMPUTE_INTERPOLATE,
        COMPUTE_PREDICT,
        ASSIGN,
        ASSIGN_KEY,
        NO_DRIFT
    }

    public ClockChangeRateMode getConfiguredClockChangeRateMode() throws MmtcException {
        if (timeCorrelationConfig.getConfig().containsKey("compute.clkchgrate.mode")) {
            final String modeFromConfig = timeCorrelationConfig.getConfig().getString("compute.clkchgrate.mode");

            if (modeFromConfig.equalsIgnoreCase("compute-predict")) {
                return ClockChangeRateMode.COMPUTE_PREDICT;
            } else if (modeFromConfig.equalsIgnoreCase("compute-interpolate")) {
                return ClockChangeRateMode.COMPUTE_INTERPOLATE;
            } else {
                throw new MmtcException(String.format("The clock change rate mode in configuration must be either 'compute-predict' or 'compute-interpolate', but it was '%s'", modeFromConfig));
            }
        }

        return DEFAULT_CLOCK_CHANGE_RATE_MODE;
    }

    /**
     * Method used for up-front validation of the presence of all required keys in TimeCorrelationAppConfig.xml. This is
     * intended to be used with the standard base config found at {$TK_CONFIG_PATH}/examples/TimeCorrelationConfigProperties-base.xml,
     * but any valid XML config can technically be used.
     *
     * @throws MmtcException if there are missing keys, or if the baseline config cannot be parsed.
     */
    public void validate() throws MmtcException {
        final ArrayList<String> missingKeys = new ArrayList<>();
        FileBasedConfiguration activeConf = timeCorrelationConfig.getConfig();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MmtcException("Failed to initialize a new DocumentBuilder while attempting to validate the configuration file.");
        }

        // Parse TimeCorrelationConfigProperties-base.xml
        ClassLoader classLoader = MmtcConfig.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(BASE_CONFIG_FILENAME)) {
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
            throw new MmtcException(String.format("Failed to validate the configuration file against %s,", BASE_CONFIG_FILENAME), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (missingKeys.isEmpty()) {
            logger.info(String.format("All required config keys validated against %s successfully.", BASE_CONFIG_FILENAME));
        } else {
            throw new MmtcException(String.format("Failed to validate TimeCorrelationConfigProperties.xml, missing %d required key(s): %s",missingKeys.size(), missingKeys));
        }
    }


    public static final List<String> REQD_SCLK_SCET_CONFIG_KEY_GROUP = Arrays.asList(
            "product.sclkScetFile.create",
            "product.sclkScetFile.dir",
            "product.sclkScetFile.baseName",
            "product.sclkScetFile.separator",
            "product.sclkScetFile.suffix",
            "product.sclkScetFile.datasetId",
            "product.sclkScetFile.producerId",
            "product.sclkScetFile.scetUtcPrecision"
    );

    public static final List<String> REQD_UPLINK_CMD_FILE_CONFIG_KEY_GROUP = Arrays.asList(
            "product.uplinkCmdFile.create",
            "product.uplinkCmdFile.outputDir",
            "product.uplinkCmdFile.baseName"
    );

    public void ensureValidSclkScetConfiguration() throws MmtcException {
        final List<String> missingKeys = getMissingSclkScetConfigurationKeys();

        if (! missingKeys.isEmpty()) {
            throw new MmtcException("SCLK-SCET operations require the following keys to be set: " + missingKeys.toString());
        }
    }

    public List<String> getMissingSclkScetConfigurationKeys() {
        return checkForMissingKeysInGroup(REQD_SCLK_SCET_CONFIG_KEY_GROUP);
    }

    public void ensureValidUplinkCmdFileConfiguration() throws MmtcException {
        List<String> missingKeys = getMissingUplinkCmdFileConfigurationKeys();

        if (! missingKeys.isEmpty()) {
            throw new MmtcException("Uplink command file operations require the following keys to be set: " + missingKeys.toString());
        }
    }

    public List<String> getMissingUplinkCmdFileConfigurationKeys() {
        return checkForMissingKeysInGroup(REQD_UPLINK_CMD_FILE_CONFIG_KEY_GROUP);
    }

    private List<String> checkForMissingKeysInGroup(List<String> requiredKeys) {
        List<String> missingKeys = new ArrayList();

        for (String key : requiredKeys) {
            if (! timeCorrelationConfig.getConfig().containsKey(key)) {
                missingKeys.add(key);
            }
        }

        return missingKeys;
    }
}
