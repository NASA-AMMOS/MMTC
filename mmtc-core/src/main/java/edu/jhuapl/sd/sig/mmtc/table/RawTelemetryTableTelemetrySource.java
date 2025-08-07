package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;
import org.apache.commons.cli.Option;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.openmbean.InvalidOpenTypeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RawTelemetryTableTelemetrySource implements TelemetrySource {
    private static final Logger logger = LogManager.getLogger();

    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.UTC;

    private static final String RAW_TLM_TABLE_PATH_CONFIG_KEY = "telemetry.source.plugin.rawTlmTable.tableFile.path";

    private TimeCorrelationAppConfig config;
    private RawTelemetryTable rawTlmTable;

    // no-arg constructor for Java service loading
    public RawTelemetryTableTelemetrySource() {

    }

    @Override
    public void applyConfiguration(TimeCorrelationAppConfig config) throws MmtcException {
        Set<String> enabledFilters = config.getFilters().keySet();
        if (
                enabledFilters.contains(TimeCorrelationAppConfig.VALID_FILTER)
        ) {
            String errorString = "When using the RawTelemetryTable telemetry source, the " +
                    TimeCorrelationAppConfig.VALID_FILTER +
                    " filters are not applicable and must be disabled by setting the configuration options " +
                    "filter.<filter name>.enabled to false.";
            throw new MmtcException(errorString);
        }

        this.config = config;

        if (! config.containsKey(RAW_TLM_TABLE_PATH_CONFIG_KEY)) {
            throw new MmtcException("To use the Raw Telemetry Table source, you must specify a value for config key " + RAW_TLM_TABLE_PATH_CONFIG_KEY);
        }

        this.rawTlmTable = new RawTelemetryTable(Paths.get(config.getString(RAW_TLM_TABLE_PATH_CONFIG_KEY)));
    }

    @Override
    public String getName() {
        return "rawTlmTable";
    }

    public void connect() {
        // no-op
    }

    public void disconnect() {
        // no-op
    }

    @Override
    public Map<String, String> sandboxTelemetrySourceConfiguration(MmtcConfig mmtcConfig, Path sandboxRoot, Path sandboxConfigRoot) throws IOException {
        final Path originalTablePath = Paths.get(mmtcConfig.getString(RAW_TLM_TABLE_PATH_CONFIG_KEY));
        final Path newTablePath = sandboxRoot.resolve(originalTablePath.getFileName());

        Files.copy(
                originalTablePath,
                newTablePath
        );

        Map<String, String> sandboxConfigChanges = new HashMap<>();
        sandboxConfigChanges.put(RAW_TLM_TABLE_PATH_CONFIG_KEY, newTablePath.toAbsolutePath().toString());
        return sandboxConfigChanges;
    }

    @Override
    public Collection<Option> getAdditionalCliArguments() {
        return Collections.emptyList();
    }

    /**
     * Read each record from the table and attempt to parse the SCLK, ERT, and
     * other data. If the ERT is within the specified time range, add it to the
     * list of samples to return.
     *
     * @param start the beginning of the ERT time range to query for timekeeping telemetry
     * @param stop the end of the ERT time range to query for timekeeping telemetry
     * @return the list of frame samples within the time range
     * @throws MmtcException when unable to parse fields from the table
     */
    @Override
    public List<FrameSample> getSamplesInRange(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {
        rawTlmTable.resetParser();

        final String dateTimePattern = config.getRawTlmTableDateTimePattern();

        final List<FrameSample> samples = new ArrayList<>();

        logger.debug("Reading RawTelemetryTable for interval " + start.toString() + " - " + stop.toString() + ".");
        int sampleCounter = 0;

        for (CSVRecord record : rawTlmTable.parser) {
            // Get the target frame UTC string to compare with the start and stop times
            String targetErtUtcStr = record.get(RawTelemetryTable.TARGET_FRAME_UTC);

            // Parse ERT as a local time but with a specified zone
            LocalDateTime localErt = LocalDateTime.parse(
                    targetErtUtcStr,
                    DateTimeFormatter.ofPattern(dateTimePattern).withZone(ZONE_OFFSET)
            );

            // Convert the "local" ERT to an offset date/time with the specified zone
            OffsetDateTime offsetErt = localErt.atOffset(ZONE_OFFSET);

            if (offsetErt.isAfter(start) && offsetErt.isBefore(stop)) {
                FrameSample sample = new FrameSample();
                sample.setSclkCoarse(Integer.parseInt(record.get(RawTelemetryTable.TARGET_FRAME_SCLK_COARSE)));
                sample.setSclkFine(Integer.parseInt(record.get(RawTelemetryTable.TARGET_FRAME_SCLK_FINE)));
                sample.setErt(new CdsTimeCode(record.get(RawTelemetryTable.TARGET_FRAME_ERT)));
                sample.setPathId(Integer.parseInt(record.get(RawTelemetryTable.PATH_ID)));
                sample.setVcid(Integer.parseInt(record.get(RawTelemetryTable.VCID)));
                sample.setVcfc(Integer.parseInt(record.get(RawTelemetryTable.VCFC)));
                sample.setMcfc(Integer.parseInt(record.get(RawTelemetryTable.MCFC)));
                sample.setTkSclkCoarse(Integer.parseInt(record.get(RawTelemetryTable.SUPPL_FRAME_SCLK_COARSE)));
                sample.setTkSclkFine(Integer.parseInt(record.get(RawTelemetryTable.SUPPL_FRAME_SCLK_FINE)));
                sample.setSuppErt(new CdsTimeCode(record.get(RawTelemetryTable.SUPPL_FRAME_ERT)));
                sample.setTkVcid(Integer.parseInt(record.get(RawTelemetryTable.VCID)));
                sample.setTkVcfc(Integer.parseInt(record.get(RawTelemetryTable.VCFC)));

                if (config.getRawTlmTableReadDownlinkDataRate()) {
                    sample.setTkDataRateBps(record.get(RawTelemetryTable.DATA_RATE_BPS));
                }

                sample.setFrameSizeBits(record.get(RawTelemetryTable.FRAME_SIZE_BITS));
                sample.setTkRfEncoding(record.get(RawTelemetryTable.RF_ENCODING));
                sample.setTkValid(true);

                samples.add(sample);

                sampleCounter++;
            }
        }

        return samples;
    }
}
