package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Class that implements the Earth Received Time (ERT) filter. If the ERT of consecutive telemetry frames
 * containing time correlation data varies by more than a configurable number of seconds from the delta 
 * between the first two samples in the set, the sample fails the filter and can be rejected.
 */
public class ErtFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Checks the consistency of a set of ERT values within a sample set.
     *
     * The difference between the first two samples in the set is calculated and used as a baseline. 
     * If the difference between subsequent samples is greater than this delta plus a configurable 
     * variance tolerance, the filter fails.
     *
     * @param samples the sample set
     * @param config the app configuration containing the max variance value
     * @return true if the set of ERT values are consistent, false otherwise
     * @throws MmtcException if propagated from a called function
     */
    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) throws MmtcException {
        if (samples.isEmpty()) {
            logger.warn("Attempted to filter an empty sample set");
            return false;
        }

        final double maxDeltaVarianceSec = config.getErtFilterMaxDeltaVarianceSec();
        CdsTimeCode lastErt = samples.get(0).getErt();

        if (samples.get(1).getErt().getDeltaSeconds(lastErt) < 0) {
            logger.error("Sample set contains unordered ERT values");
            return false;
        }

        final double firstFrameDeltaSec = Math.abs(samples.get(1).getErt().getDeltaSeconds(lastErt));
        logger.debug(String.format("ERT Filter: Baseline frame delta from first two samples: %f seconds; Acceptable variance: +/- %f seconds", firstFrameDeltaSec, maxDeltaVarianceSec));

        // For every sample excluding the first
        for (int i = 1; i < samples.size(); i++) {
            FrameSample sample = samples.get(i);
            double deltaSeconds = Math.abs(sample.getErt().getDeltaSeconds(lastErt));

            try {
                logger.debug(String.format("ERT Filter: Current ERT: %s; Previous ERT: %s; Delta seconds: %f",
                        sample.getErtStr(),
                        TimeConvert.cdsToIsoUtc(lastErt),
                        deltaSeconds));

                if (Math.abs(firstFrameDeltaSec - deltaSeconds) > maxDeltaVarianceSec) {
                    logger.warn(String.format("ERT Filter failed: Current ERT: %s; Previous ERT: %s. Delta seconds of %f" +
                                    " exceeds threshold of %f +/- %f seconds",
                            sample.getErtStr(),
                            TimeConvert.cdsToIsoUtc(lastErt),
                            deltaSeconds,
                            firstFrameDeltaSec,
                            maxDeltaVarianceSec));
                    return false;
                }

            } catch (TimeConvertException e) {
                throw new MmtcException("Failed to convert between CDS and UTC timecodes", e);
            }

            lastErt = sample.getErt();
        }

        return true;
    }
}
