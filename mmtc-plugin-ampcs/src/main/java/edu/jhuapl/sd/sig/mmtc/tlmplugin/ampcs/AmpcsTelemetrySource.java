package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingPacketParser;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals.ChanValReadConfig;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals.ChanValsReader;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals.SingleChanValReader;
import org.apache.commons.cli.Option;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.jhuapl.sd.sig.mmtc.util.Environment;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

/**
 * <P>The AmpcsTelemetrySource class provides shared base functionality for telemetry sources that retrieve telemetry
 * using AMPCS command line tools. (Specifically, this class does not provide functionality for using MCWS.)
 * This is a parent class from which classes that interface with the AMPCS telemetry archive are to be
 * implemented.</P>
 */
public abstract class AmpcsTelemetrySource implements TelemetrySource {
    public static final String AMPCS_SESSION_ID_OPT = "AMPCS Session ID";
    public static final String ADDITIONAL_AMPCS_CLI_ARGS_OPT = "Additional AMPCS CLI args";

    protected static final Logger logger = LogManager.getLogger();

    protected MmtcConfigWithTlmSource config;
    protected AmpcsTelemetrySourceConfig ampcsConfig;

    protected String chillGdsPath;

    /**
     * The AMPCS session ID.
     */
    protected String sessionId = null;

    /**
     * Any additional AMPCS arguments.
     */
    protected String connectionParms = null;

    /**
     * The timeout to apply when running chill_* queries.
     */
    protected int chillTimeoutSec;

    // Set to true if AMPCS is available on the host system.
    protected boolean connectedToAmpcs = false;

    // ExecutorService to manage a thread pool to consume subprocess pipes.
    private ExecutorService executorService;


    public AmpcsTelemetrySource() {
    }

    @Override
    public void applyConfiguration(MmtcConfigWithTlmSource config) throws MmtcException {
        this.config = config;
        this.ampcsConfig = new AmpcsTelemetrySourceConfig(config);
        this.connectionParms = ampcsConfig.getConnectionParms();
        setChillTimeout(ampcsConfig.getChillTimeoutSec());

        logger.info("Downlink data rate will be parsed from packets: " + packetsHaveDownlinkDataRate());
        logger.info("Valid flag will be parsed from packets: " + packetsHaveInvalidFlag());
    }

    @Override
    public List<AdditionalOption> getAdditionalOptions() {
        Option ampcsSessionIdOpt = new Option(
                "K",
                "ampcs-session-id",
                true,
                "Specifies the session ID when using an AMPCS telemetry source."
        );

        Option connectionParmsOpt = new Option(
                "n",
                "connection-parms",
                true,
                "Provides any additional CLI parameters that will be passed down to the AMPCS CLI tools."
        );

        return Arrays.asList(
                new AdditionalOption(
                        AMPCS_SESSION_ID_OPT,
                        ampcsSessionIdOpt
                ),
                new AdditionalOption(
                        ADDITIONAL_AMPCS_CLI_ARGS_OPT,
                        connectionParmsOpt
                )
        );
    }

    @Override
    public void applyOption(String name, String value) throws MmtcException {
        switch(name) {
            case AMPCS_SESSION_ID_OPT: {
                setSessionId(value);
                break;
            }
            case ADDITIONAL_AMPCS_CLI_ARGS_OPT: {
                this.connectionParms = value;
                break;
            }
            default: {
                throw new IllegalArgumentException("Unrecognized option name: " + name);
            }
        }
    }

    @Override
    public Map<String, String> sandboxTelemetrySourceConfiguration(MmtcConfig mmtcConfig, Path sandboxRoot, Path sandboxConfigRoot) throws IOException {
        final Path originalTkPacketDescriptionFilePath = Paths.get(mmtcConfig.getString("telemetry.source.plugin.ampcs.tkpacket.tkPacketDescriptionFile.path"));
        final Path newTkPacketDescriptionFilePath = sandboxConfigRoot.resolve(originalTkPacketDescriptionFilePath.getFileName());

        Files.copy(
                originalTkPacketDescriptionFilePath,
                newTkPacketDescriptionFilePath
        );

        final Map<String, String> sandboxConfigChanges = new HashMap<>();
        sandboxConfigChanges.put("telemetry.source.plugin.ampcs.tkpacket.tkPacketDescriptionFile.path", newTkPacketDescriptionFilePath.toAbsolutePath().toString());
        return sandboxConfigChanges;
    }

    /**
     * The AMPCS system does not require a connection action other than specifying a session ID. This method sets
     * the session ID to the supplied value and then verifies that AMPCS is available on the system. This function
     * will throw an exception if it is not.
     *
     * @throws MmtcException if the AMPCS is not available on the system
     */
    @Override
    public void connect() throws MmtcException {
        // No connection activity is needed for AMPCS, but execute a chill
        // command to verify that it is available on the system.

        logger.info("Connecting...");
        
        try {
            chillGdsPath = Environment.getEnvironmentVariable("CHILL_GDS");
            if (chillGdsPath == null) {
                throw new MmtcException("Environment variable $CHILL_GDS is not set.");
            }

            executorService = Executors.newCachedThreadPool();
            // run a command through runSubprocess to test if we're connected to AMPCS.
            // however, runSubprocess refuses to run unless we *are* connected to AMPCS, so
            // temporarily set the connectedToAmpcs flag to true
            connectedToAmpcs = true;
            String stdout = runSubprocessIgnoreExitCode(chillGdsPath + "/bin/chill_get_packets -v");
            // now reset the connection flag and then check the output of runSubprocess to
            // determine whether or not we're really connected.
            connectedToAmpcs = false; // now reset the flag

            if (stdout.contains("AMPCS")) {
                logger.debug("Verified that MMTC is able to call AMPCS chill_get_packets.");
                if (this.sessionId != null) {
                    logger.debug(String.format("Calls to AMPCS will use session ID %s.", this.sessionId));
                } else {
                    logger.debug("Calls to AMPCS will not use a session ID.");
                }
                connectedToAmpcs = true;
            }

            if (!connectedToAmpcs) {
                executorService.shutdownNow();
                throw new MmtcException("Unable to connect to AMPCS.");
            }
        } catch (IOException e) {
            executorService.shutdownNow();
            throw new MmtcException("Unable to execute subprocess.", e);
        }

        logger.info("Connected.");
    }

    private void setSessionId(String sessionId) throws MmtcException {
        // Allow null; otherwise, verify that the session ID is a positive integer.
        if (sessionId == null || (sessionId.matches("\\d+") && !sessionId.matches("0+"))) {
            this.sessionId = sessionId;
        } else {
            throw new MmtcException(String.format(
                    "Invalid session ID %d.  Session ID is optional, but if specified, it must be a positive integer.", sessionId
            ));
        }
    }

    private void setChillTimeout(int timeoutSec) throws MmtcException {
        if (timeoutSec > 0) {
            this.chillTimeoutSec = timeoutSec;
        }
        else {
            throw new MmtcException(String.format(
                    "Invalid chill timeout %d. It must be a positive integer.", timeoutSec
            ));
        }
    }

    /**
     * AMPCS requires no 'disconnect' action; we're just shutting down the execution service.
     */
    public void disconnect() {
        logger.info("Disconnecting...");
        if (connectedToAmpcs) {
            connectedToAmpcs = false;
            executorService.shutdownNow();
        }
        logger.info("Disconnected.");
    }

    protected boolean packetsHaveDownlinkDataRate() throws MmtcException {
        try {
            return new TimekeepingPacketParser(
                    ampcsConfig.getTkPacketDescriptionFilePath()
            ).packetsHaveDownlinkDataRate();
        } catch (Exception e) {
            throw new MmtcException("Unable to read or parse packet definition file " + ampcsConfig.getTkPacketDescriptionFilePath(), e);

        }
    }

    protected boolean packetsHaveInvalidFlag() throws MmtcException {
        try {
            return new TimekeepingPacketParser(
                    ampcsConfig.getTkPacketDescriptionFilePath()
            ).packetsHaveInvalidFlag();
        } catch (Exception e) {
            throw new MmtcException("Unable to read or parse packet definition file " + ampcsConfig.getTkPacketDescriptionFilePath());
        }
    }

    /**
     * Gets the Guidance, Navigation, and Control (GNC) parameters. These are
     * the GNC SCLK, TDT(S), SCLK1, TDT1, and CLKRATE1 parameters used to
     * translate SCLK to TDT onboard the spacecraft. These are initially set by
     * the parameters written to the Uplink Command File.
     *
     * SCLK1, TDT1, CLKRATE1 are initially provided from the ground. GNC SCLK
     * and TDT(S) are computed by the spacecraft onboard.
     *
     * @param noEarlierThanScet spacecraft event time associated with the channel value
     * @param noEarlierThanTdtS the earliest allowable TDT for TDT(S) and SCLK for TDT(S) values
     * @return the GNC parameters
     */
    @Override
    public GncParms getGncTkParms(OffsetDateTime noEarlierThanScet, Double noEarlierThanTdtS) {
        if (!connectedToAmpcs) {
            throw new IllegalStateException("Not connected to AMPCS.");
        }

        GncParms gnc_parms = new GncParms();

        try {
            // Create a bounding time range, looking forward a number of seconds from the given SCET for querying channel values
            String beginTime = noEarlierThanScet.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            beginTime = beginTime.replace("Z", "");

            OffsetDateTime endTimeAsOffsetDateTime = noEarlierThanScet.plusSeconds(config.getTkParmWindowSec());
            String endTime = endTimeAsOffsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            endTime = endTime.replace("Z", "");

            String queryCmd = chillGdsPath + "/bin/chill_get_chanvals --showColumns ";

            if (sessionId != null) {
                queryCmd += String.format("-K %s ", sessionId);
            }

            queryCmd += String.format("--timeType SCET --beginTime %s --endTime %s ", beginTime, endTime);

            final Optional<ChanValReadConfig> gncSclkChannelConfig     = ampcsConfig.getGncChanValReadConfig("gncsclk", true);
            final Optional<ChanValReadConfig> tdtSChannelConfig        = ampcsConfig.getGncChanValReadConfig("tdts", true);
            if (tdtSChannelConfig.isPresent()) {
                tdtSChannelConfig.get().setAcceptanceCriterion((val) -> val >= noEarlierThanTdtS);
            }

            final Optional<ChanValReadConfig> sclk1ChannelConfig       = ampcsConfig.getGncChanValReadConfig("sclk1", false);
            final Optional<ChanValReadConfig> tdt1ChannelConfig        = ampcsConfig.getGncChanValReadConfig("tdt1", false);
            final Optional<ChanValReadConfig> clkChgRate1ChannelConfig = ampcsConfig.getGncChanValReadConfig("tdtChgRate", false);

            List<ChanValReadConfig> channelConfigs = Stream.of(gncSclkChannelConfig, tdtSChannelConfig, sclk1ChannelConfig, tdt1ChannelConfig, clkChgRate1ChannelConfig)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            if (channelConfigs.isEmpty()) {
                logger.info("No GNC parameter channel IDs were specified, and thus none will be queried.");
                return gnc_parms;
            }

            queryCmd += "--channelIds " + channelConfigs.stream().map(c -> c.channelId).collect(Collectors.joining(","));

            if (connectionParms != null) {
                queryCmd += " " + connectionParms;
            }

            // Get the GNC parameter channels associated with the packet.
            CSVParser channelValues = runSubprocessCsv(queryCmd);

            ChanValsReader channelValuesReader = new ChanValsReader(ampcsConfig, channelConfigs, noEarlierThanScet);

            for (CSVRecord channelRow : channelValues) {
                channelValuesReader.read(channelRow);
            }

            if (gncSclkChannelConfig.isPresent() && tdtSChannelConfig.isPresent()) {
                // if both the GNC SCLK and TDT(S) channels are specified, their values as measured at the same SCET should be used

                Map<String, Double> sameScetValuesClosestToTarget = channelValuesReader.getPairedValuesForSameScetNoEarlierThan(noEarlierThanScet, gncSclkChannelConfig.get().channelId, tdtSChannelConfig.get().channelId);
                gnc_parms.setGncsclk(sameScetValuesClosestToTarget.get(gncSclkChannelConfig.get().channelId));
                gnc_parms.setTdt_s(sameScetValuesClosestToTarget.get(tdtSChannelConfig.get().channelId));
            } else {
                // else, best effort to get whichever was specified

                gncSclkChannelConfig.ifPresent(c -> gnc_parms.setGncsclk(channelValuesReader.getValueFor(c.channelId)));
                tdtSChannelConfig.ifPresent(c -> gnc_parms.setTdt_s(channelValuesReader.getValueFor(c.channelId)));
            }

            // read the rest at the closest time to, but after, the correlation as possible
            // no mission we know of right now downlinks these as channel values, but if they did, this could be used and would return a coherent set of values so long as they were timestamped (SCET-wise) the same as each other
            sclk1ChannelConfig.ifPresent(c -> gnc_parms.setSclk1(channelValuesReader.getValueFor(c.channelId)));
            tdt1ChannelConfig.ifPresent(c -> gnc_parms.setTdt1(channelValuesReader.getValueFor(c.channelId)));
            clkChgRate1ChannelConfig.ifPresent(c -> gnc_parms.setClkchgrate1(channelValuesReader.getValueFor(c.channelId)));

            if (gnc_parms.isEmpty()) {
                logger.warn("No GNC parameters were found in the sampling interval.");
            }

        // A failure to retrieve the GNC parameters will result in an incomplete entry for the Time History File,
        // but does not prevent the essential time correlation computations. Therefore, such a failure is treated
        // as a non-fatal error and logged.
        } catch (IOException e) {
            logger.error("I/O exception handled while attempting to retrieve time correlation GNC parameters. ", e);
            return new GncParms();
        }

        return gnc_parms;
    }

    /**
     * Gets the temperature of the active oscillator associated with the specified SCET. If no value can be
     * found, returns NaN.
     *
     * @param scet           IN: the spacecraft event time around which to look for the temperature data
     * @param oscillatorId  IN: the specified oscillator ID
     * @return the oscillator temperature
     * @throws MmtcException if the selected oscillator is invalid
     */
    public double getOscillatorTemperature(OffsetDateTime scet, String oscillatorId) throws MmtcException {
        if (! connectedToAmpcs) {
            throw new IllegalStateException("Not connected to AMPCS.");
        }

        logger.debug("Getting the temperature for oscillator " + oscillatorId + ".");

        Optional<ChanValReadConfig> oscTempChanValReadConfig = ampcsConfig.getOscTempChanValReadConfig(oscillatorId);

        if (! oscTempChanValReadConfig.isPresent()) {
            logger.info("Skipping retrieval of oscillator temperature, as channel value information was not provided in configuration.");
            return Double.NaN;
        }

        final String tkOscTempChannelId = oscTempChanValReadConfig.get().channelId;
        final String tkOscTempReadField = oscTempChanValReadConfig.get().readField;

        // Create a bounding time +/- seconds to query the channels at.
        OffsetDateTime time_minus = scet.minusSeconds(config.getTkOscTempWindowSec());
        String beginTime = time_minus.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
        beginTime = beginTime.replace("Z", "");

        OffsetDateTime time_plus = scet.plusSeconds(config.getTkOscTempWindowSec());
        String endTime = time_plus.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
        endTime = endTime.replace("Z", "");

        logger.debug("Obtaining temperature for oscillator " + oscillatorId + " (from channel ID "
                + tkOscTempChannelId + ").");

        String queryCmd = chillGdsPath+"/bin/chill_get_chanvals --showColumns";
        if (sessionId != null) {
            queryCmd += " -K " + sessionId;
        }

        queryCmd += " --timeType SCET --channelIds " + tkOscTempChannelId +
                " --beginTime "  + beginTime +
                " --endTime "    + endTime;

        if (connectionParms != null) {
            queryCmd += " " + connectionParms;
        }

        // Get the channels associated with the packet.
        CSVParser channelValueOutputs;
        try {
            channelValueOutputs = runSubprocessCsv(queryCmd);
        } catch (IOException e) {
            throw new MmtcException("Unable to retrieve oscillator temperature", e);
        }

        SingleChanValReader channelValueReader = new SingleChanValReader(ampcsConfig, new ChanValReadConfig(tkOscTempChannelId, tkOscTempReadField), scet);

        for (CSVRecord channelValueRow : channelValueOutputs) {
            channelValueReader.read(channelValueRow);
        }

        double oscillatorTemperature = channelValueReader.getValueClosestToTargetScet();

        if (Double.isNaN(oscillatorTemperature)) {
            logger.warn("No oscillator temperature data found in the sampling interval (channel ID " + tkOscTempChannelId + ").");
        }

        return oscillatorTemperature;
    }

    /**
     * Runs a command as a subprocess, waits for it to complete, and checks that its exit code is
     * 0. Logs everything that the subprocess writes to stderr. Returns everything that the
     * subprocess writes to stdout.
     * 
     * The current implementation throws if an error occurs. If that happens, then subsequent calls
     * to this method, {@code runSubprocessCsv(String)}, {@code runSubprocess(String, boolean)},
     * and {@code runSubprocessIgnoreExitCode(String)} are no longer valid, may throw runtime
     * exceptions, and should be avoided until the {@code AmpcsTelemetrySource} instance is reset.
     * To reset the instance, call {@code disconnect()} and then {@code connect(String)} again.
     *
     * @param cmd IN: the command text to execute
     * @return the process's stdout
     * @throws IOException if an error occurred during the run or while reading stdout/stderr, or
     *   if the subprocess returns a nonzero exit code.
     */
    protected String runSubprocess(String cmd) throws IOException {
        return runSubprocess(cmd, false);
    }

    /**
     * Runs a command as a subprocess and waits for it to complete. Logs everything that the
     * subprocess writes to stderr. Returns everything that the subprocess writes to stdout.
     * 
     * The current implementation throws if an error occurs. If that happens, then subsequent calls
     * to this method, {@code runSubprocessCsv(String)}, {@code runSubprocess(String)}, and
     * {@code runSubprocess(String, boolean)} are no longer valid, may throw runtime exceptions,
     * and should be avoided until the {@code AmpcsTelemetrySource} instance is reset. To reset the
     * instance, call {@code disconnect()} and then {@code connect(String)} again.
     *
     * @param cmd IN: the command text to execute
     * @return the process's stdout
     * @throws IOException if an error occurred during the run or while reading stdout/stderr
     */
    protected String runSubprocessIgnoreExitCode(String cmd) throws IOException {
        return runSubprocess(cmd, true);
    }

    /**
     * Runs a command as a subprocess, waits for it to complete, and optionally checks that its
     * exit code is 0. Logs everything that the subprocess writes to stderr. Returns everything
     * that the subprocess writes to stdout.
     * 
     * The current implementation throws if an error occurs. If that happens, then subsequent calls
     * to this method, {@code runSubprocessCsv(String)}, {@code runSubprocess(String)}, and
     * {@code runSubprocessIgnoreExitCode(String)} are no longer valid, may throw runtime
     * exceptions, and should be avoided until the {@code AmpcsTelemetrySource} instance is reset.
     * To reset the instance, call {@code disconnect()} and then {@code connect(String)} again.
     *
     * @param cmd IN: the command text to execute
     * @param ignoreExitCode IN: whether to allow (and not throw on) nonzero exit codes
     * @return the process's stdout
     * @throws IOException if an error occurred during the run or while reading stdout/stderr
     */
    protected String runSubprocess(String cmd, boolean ignoreExitCode) throws IOException {
        if (! connectedToAmpcs) {
            throw new IllegalStateException("Not connected to AMPCS.");
        }

        logger.debug("Executing this command as a subprocess: " + cmd);

        // Process provides control of native processes started by ProcessBuilder.start and Runtime.exec.
        // getRuntime() returns the runtime object associated with the current Java application.
        String cmds[] = cmd.split(" ");
        Process p = Runtime.getRuntime().exec(cmds);

        // We use an ExecutorService thread pool to concurrently consume both stderr and stdout
        // because if either pipe fills up, the process might block or deadlock. Here we submit a
        // (anonymous) Callable to the thread pool to copy everything from stderr to the log.
        Future<?> stderrReader = executorService.submit(() -> {
            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            // read any errors from the attempted execution
            while ((line = stderr.readLine()) != null) {
                logger.error("subprocess stderr: " + line);
            }
            return null;
        });

        // Here we submit another Callable to store everything from stdout so we can return it to
        // the caller.
        final StringBuffer stdoutStringBuffer = new StringBuffer();
        Future<?> stdoutReader = executorService.submit(() -> {
            BufferedReader stdoutBuffer = new BufferedReader(new InputStreamReader(p.getInputStream()));
            stdoutStringBuffer.append(stdoutBuffer.lines().collect(Collectors.joining(System.lineSeparator())));
            return null;
        });

        // wait for the executorService to complete the stdout and stderr reading tasks
        try {
            stderrReader.get(chillTimeoutSec, TimeUnit.SECONDS);
            stdoutReader.get(chillTimeoutSec, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            executorService.shutdownNow();
            p.destroyForcibly();
            throw new IOException("Error reading process stdout or stderr", e);
        }

        // wait for the process to exit, and then ensure its exit value is 0
        try {
            if (! p.waitFor(chillTimeoutSec, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                p.destroyForcibly();
                throw new IOException(String.format("Process did not complete after %d seconds", chillTimeoutSec));
            }

            if (!ignoreExitCode && p.exitValue() != 0) {
                executorService.shutdownNow();
                throw new IOException(String.format("Process exited with code %d", p.exitValue()));
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            p.destroyForcibly();
            throw new IOException("Thread interrupted while waiting for chill_get_packets to exit", e);
        }

        // return stdout
        return stdoutStringBuffer.toString();
    }

    /**
     * Runs a command as a subprocess. Outputs an array of strings. Each string contains one line
     * of output.
     * 
     * The current implementation may throw due to calling {@code runSubprocess(String)}. If that
     * happens, then subsequent calls to this method, {@code runSubprocess(String)},
     * {@code runSubprocess(String, boolean)}, and {@code runSubprocessIgnoreExitCode(String)} are
     * no longer valid, may throw runtime exceptions, and should be avoided until the
     * {@code AmpcsTelemetrySource} instance is reset. To reset the instance, call
     * {@code disconnect()} and then {@code connect(String)} again.
     *
     * @param  cmd IN: the command text to execute
     * @return the return a CSV parser object containing the stdout from the command
     * @throws IOException if an error occurred during the command run
     */
    protected CSVParser runSubprocessCsv(String cmd) throws IOException {
        return CSVParser.parse(runSubprocess(cmd), CSVFormat.DEFAULT.withFirstRecordAsHeader());
    }

    /**
     * @return true if AMPCS is available and active.
     */
    public boolean isConnectedToAmpcs() { return this.connectedToAmpcs; }

    /**
     * @return the current AMPCS session ID
     */
    public String getSessionId() { return this.sessionId; }

    /**
     * Obtains the current active oscillator from configration parameters.
     * @param targetSample not used in this implementation
     * @return the active oscillator
     */
    @Override
    public String getActiveOscillatorId(FrameSample targetSample) {
        switch(ampcsConfig.getActiveOscillatorSelectionMode()) {
            case none: {
                logger.info("Active oscillator ID not set");
                return "-";
            }
            case fixed: {
                String activeOscillatorId = ampcsConfig.getFixedActiveOscillatorId();
                logger.info("Active oscillator ID is set by configuration to a fixed value of " + activeOscillatorId);
                return activeOscillatorId;
            }
            case by_vcid: {
                String activeOscillatorId = ampcsConfig.getActiveOscillatorIdByVcid(targetSample.getTkVcid());
                logger.info(String.format("VCID %d used to look up the active oscillator ID value of %s", targetSample.getTkVcid(), activeOscillatorId));
                return activeOscillatorId;
            }
            default:
                throw new IllegalStateException("Invalid active oscillator selection mode; please check configuration.");
        }
    }

    /**
     * Creates a unique, temporary file path.
     *
     * @param extension IN:the file extension
     * @return the file path
     */
    public static String getUniqueFilename(String extension) {
        String fileName = MessageFormat.format("{0}.{1}", UUID.randomUUID(), extension.trim());
        return Paths.get(System.getProperty("java.io.tmpdir"), fileName).toString();
    }

    /**
     * Obtains the current active Radio identifier from configuration parameters. In some missions
     * an oscillator on the radio provides the SCLK values used for time correlation. In this case,
     * it is set by a configuration parameter.
     *
     * @return the current active radio ID from configuration parameters
     */
    @Override
    public String getActiveRadioId(FrameSample targetSample) {return ampcsConfig.getActiveRadioId();}
}