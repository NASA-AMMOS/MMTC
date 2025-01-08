package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Class that implements the Minimum Data Rate Filter. If the downlink data rate of a frame containing time
 * correlation information is lower than a configurable rate in bits/second, the sample fails the filter and
 * can be rejected.
 */
public class MinDataRateFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Check the data rate for each sample in a list and verify that it is greater than or equal to
     * the minimum specified rate threshold.
     *
     * @param samples the sample list
     * @param config the app config containing the minimum data rate
     * @return true if all samples have a data rate no lower than the minimum
     */
    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) {
        if (samples.isEmpty()) {
            logger.warn("Data Rate Filter failed: Attempted to filter an empty sample set");
            return false;
        }

        // Get the minimum data rate in bbs from the configuration parameters.
        int minRateBps = config.getDataRateFilterMinRateBps();

        for (FrameSample sample : samples) {
            double sampleRateBps = sample.getTkDataRateBps().doubleValue();

            logger.debug(String.format("MinDataRateFilter: Comparing sample at ERT (" + sample.getErt().toString() + ") "
                    + sample.getErtStr() + " data rate %f bps with minimum rate %d bps", sampleRateBps, minRateBps));

            if (sampleRateBps < minRateBps) {
                logger.warn(String.format(
                        "Minimum Data Rate Filter failed: Sample with ERT %s has data rate of %f bps, which is less than the minimum allowed of %d bps",
                        sample.getErtStr(),
                        sampleRateBps,
                        minRateBps
                ));
                return false;
            }
        }

        return true;
    }
}