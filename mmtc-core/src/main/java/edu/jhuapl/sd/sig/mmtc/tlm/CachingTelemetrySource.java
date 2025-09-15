package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.TelemetryCache;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A TelemetrySource that caches retrieved FrameSamples from an underlying 'actual' TelemetrySource that it delegates to.
 * Telemetry is cached in a locally-managed sqlite database file.
 */
public class CachingTelemetrySource implements TelemetrySource {
    private final TelemetrySource underlyingTelemetrySource;
    private final TelemetryCache telemetryCache;

    public CachingTelemetrySource(Path telemetrySourceCacheFilepath, TelemetrySource underlyingTelemetrySource) throws IOException {
        this.underlyingTelemetrySource = underlyingTelemetrySource;
        this.telemetryCache = new TelemetryCache(underlyingTelemetrySource, telemetrySourceCacheFilepath);
    }

    @Override
    public String getName() {
        return underlyingTelemetrySource.getName();
    }

    @Override
    public Collection<Option> getAdditionalCliArguments() {
        return underlyingTelemetrySource.getAdditionalCliArguments();
    }

    @Override
    public void applyConfiguration(TimeCorrelationAppConfig config) throws MmtcException {
        underlyingTelemetrySource.applyConfiguration(config);
    }

    @Override
    public void connect() throws MmtcException {
        underlyingTelemetrySource.connect();
    }

    @Override
    public void disconnect() throws MmtcException {
        underlyingTelemetrySource.disconnect();
    }

    @Override
    public Map<String, String> sandboxTelemetrySourceConfiguration(MmtcConfig mmtcConfig, Path sandboxRoot, Path sandboxConfigRoot) throws IOException {
        return underlyingTelemetrySource.sandboxTelemetrySourceConfiguration(mmtcConfig, sandboxRoot, sandboxConfigRoot);
    }

    @Override
    public List<FrameSample> getSamplesInRange(OffsetDateTime startErt, OffsetDateTime stopErt) throws MmtcException {
        return telemetryCache.getSamplesInRange(startErt, stopErt);
    }

    @Override
    public String getActiveOscillatorId(FrameSample targetSample) {
        return underlyingTelemetrySource.getActiveOscillatorId(targetSample);
    }

    @Override
    public double getOscillatorTemperature(OffsetDateTime scet, String oscillatorId) throws MmtcException {
        return underlyingTelemetrySource.getOscillatorTemperature(scet, oscillatorId);
    }

    @Override
    public String getActiveRadioId(FrameSample targetSample) {
        return underlyingTelemetrySource.getActiveRadioId(targetSample);
    }

    @Override
    public GncParms getGncTkParms(OffsetDateTime noEarlierThanScet, Double noEarlierThanTdtS) {
        return underlyingTelemetrySource.getGncTkParms(noEarlierThanScet, noEarlierThanTdtS);
    }

    public Map<String, String> getCacheStatistics() throws IOException {
        return telemetryCache.getCacheStatistics();
    }
}
