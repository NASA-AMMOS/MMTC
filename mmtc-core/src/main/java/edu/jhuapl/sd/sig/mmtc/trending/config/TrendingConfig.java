package edu.jhuapl.sd.sig.mmtc.trending.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionAndAdjustmentOptions;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;

import java.time.OffsetDateTime;
import java.util.Optional;

public class TrendingConfig extends MmtcConfigWithTlmSource {
    public TrendingConfig() throws Exception {
        super();
    }

    @Override
    public void validate() throws MmtcException {
        super.validate();
        super.validateMigrationNotNeeded();
    }
}
