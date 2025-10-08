package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Class that implements the SCLK filter. If the time correlation SCLK of consecutive samples of time correlation data
 * (e.g., SCLK in a TK packet or in a transfer frame secondary header) differs by more than a configurable threshold,
 * the sample fails the filter and is rejected.
 */
public class SclkFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Check the consistency of a set of SCLK values within a sample set.
     *
     * The difference between each SCLK in a sample and the previous sample SCLK is
     * compared to the delta between the first two values plus a configurable limit. 
     * If the difference is greater than this dynamic threshold, the filter fails.
     *
     * @param samples the sample set
     * @param config the app configuration containing the variance threshold value
     * @return true if the set of SCLK values are consistent, false otherwise
     */
    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) throws MmtcException {
        if (samples.isEmpty()) {
            logger.warn("Attempted to filter an empty sample set");
            return false;
        }

        final double maxDeltaVarianceSec = config.getSclkFilterMaxDeltaVarianceSec();
        final int tkSclkFineTickModulus;

        try {
            tkSclkFineTickModulus = config.getTkSclkFineTickModulus();
        } catch (TimeConvertException e) {
            throw new MmtcException(e.getMessage(), e);
        }

        double lastSclkSeconds = samples.get(0).getTkSclkComposite(tkSclkFineTickModulus);

        if (samples.get(1).getTkSclkComposite(tkSclkFineTickModulus) < lastSclkSeconds) {
            logger.error("Sample set contains unordered SCLK values");
            return false;
        }

        final double firstFrameDeltaSec = (samples.get(1).getTkSclkComposite(tkSclkFineTickModulus)) - lastSclkSeconds;
        logger.debug(String.format("SCLK Filter: Baseline frame delta from first two samples: %f seconds; Acceptable variance: +/- %f seconds", firstFrameDeltaSec, maxDeltaVarianceSec));
    

        // For every sample excluding the first
        for (int i = 1; i < samples.size(); i++) {
            FrameSample sample = samples.get(i);
            logger.debug("Evaluating sample with ERT " + sample.getErtStr() + ".");
            final double currentSclkSeconds = sample.getTkSclkComposite(tkSclkFineTickModulus);

            if (currentSclkSeconds <= 0) {
                logger.warn(String.format("SCLK Filter cannot verify sample at ERT %s, as it does not have a value set for tkSclkCoarse", sample.getErtStr()));
            } else if (lastSclkSeconds <= 0) {
                logger.warn(String.format("SCLK Filter cannot verify sample at ERT %s, as the prior FrameSample did not have a value set for tkSclkCoarse", sample.getErtStr()));
            } else {
                final double deltaSeconds = Math.abs(currentSclkSeconds - lastSclkSeconds);
                logger.debug(String.format("SCLK Filter: Current SCLK seconds: %f; Previous SCLK seconds: %f; Delta seconds: %f)", currentSclkSeconds, lastSclkSeconds, deltaSeconds));

                // Fail filter if deltaSeconds is maxDeltaVarianceSec seconds past firstFrameDeltaSec baseline in either direction
                if (Math.abs(firstFrameDeltaSec - deltaSeconds) > maxDeltaVarianceSec) {
                    logger.warn(String.format(
                            "SCLK Filter failed: Sample at ERT %s: Current SCLK seconds: %f; Previous SCLK seconds: %f. Delta seconds of %f exceeds threshold of %f +/- %f seconds.",
                            sample.getErtStr(),
                            currentSclkSeconds,
                            lastSclkSeconds,
                            deltaSeconds,
                            firstFrameDeltaSec,
                            maxDeltaVarianceSec
                    ));

                    return false;
                }
            }

            lastSclkSeconds = currentSclkSeconds;
        }

        return true;
    }
}
