package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * Class that implements the ground stations filter. This filter rejects time correlation data that is received
 * by ground stations not included in a configurable list. Telemetry received by a ground station not included in
 * that list is rejected.
 */
public class GroundStationFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Check whether all samples are:
     *  (1) from the same ground station, and
     *  (2) that ground station is included in the configuration
     *
     * @param samples the sample set
     * @return true if both conditions are met, false otherwise
     */
    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) {
        if (samples.isEmpty()) {
            logger.warn("Ground Station Filter Failed: Attempted to filter an empty sample set");
            return false;
        }

        List<Integer> pathIds;
        String[] strs = config.getGroundStationFilterPathIds();
        Integer[] ints = new Integer[strs.length];
        final int firstPathId = samples.get(0).getPathId();

        for (int i = 0; i < ints.length; i++) {
            ints[i] = Integer.parseInt(strs[i]);
        }

        pathIds = Arrays.asList(ints);

        // Check if the first path ID is valid
        if (!pathIds.contains(firstPathId)) {
            logger.warn(String.format(
                    "Ground Station Filter Failed: first sample (ERT %s) has path ID %d, which is not a valid path ID.",
                    samples.get(0).getErtStr(),
                    firstPathId
            ));
            return false;
        }

        // Check if the rest of the path IDs match the first
        for (FrameSample sample : samples) {
            if (sample.getPathId() != firstPathId) {
                logger.warn(String.format("Ground Station Filter Failed: sample with ERT %s has path ID %d, which differs from previous sample's path ID %d.",
                        sample.getErtStr(), sample.getPathId(), firstPathId));
                return false;
            }
        }

        return true;
    }
}
