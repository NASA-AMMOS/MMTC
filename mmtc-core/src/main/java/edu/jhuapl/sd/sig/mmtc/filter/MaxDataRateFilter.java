package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Class that implements the Maximum Data Rate Filter. If the downlink data rate of a frame containing time
 * correlation information is greater than a configurable rate in bits/second, the sample fails the filter and
 * can be rejected.
 */
public class MaxDataRateFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Check the data rate for each sample in a list and verify that it is less than or equal to the maximum
     * specified rate threshold.
     *
     * @param samples the sample list
     * @param config the app config containing the maximim data rate
     * @return true if all samples have a data rate no higher than the maximum
     */
    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationRunConfig config) {
        if (samples.isEmpty()) {
            logger.warn("Data Rate Filter failed: Attempted to filter an empty sample set");
            return false;
        }

        // Get the maximum data rate in bbs from the configuration parameters.
        int maxRateBps = config.getDataRateFilterMaxRateBps();

        for (FrameSample sample : samples) {
            double sampleRateBps = sample.getTkDataRateBps().doubleValue();

            logger.debug(String.format("MaxDataRateFilter: Comparing sample at ERT (" + sample.getErt().toString() + ") "
                    + sample.getErtStr() + " data rate %f bps with maximum rate %d bps", sampleRateBps, maxRateBps));

            if (sampleRateBps > maxRateBps) {
                logger.warn(String.format(
                        "Maximum Data Rate Filter failed: Sample with ERT %s has data rate of %f bps, which is greater than the maximum allowed of %d bps",
                        sample.getErtStr(),
                        sampleRateBps,
                        maxRateBps
                ));
                return false;
            }
        }

        return true;
    }
}