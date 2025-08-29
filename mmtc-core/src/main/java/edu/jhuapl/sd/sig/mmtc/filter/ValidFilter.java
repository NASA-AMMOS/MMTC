package edu.jhuapl.sd.sig.mmtc.filter;

import java.util.List;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

/**
 * Class that implements the valid sample filter. If any samples in the set
 * are flagged as invalid, then the set fails the filter.
 */
public class ValidFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Check the valid flags of a set of samples. If any sample is flagged
	 * invalid, the filter fails. The valid flag in the packet is assumed
     * to be set to 0 if the packet is valid and 1 if it is invalid.
     *
     * @param samples the sample set
     * @param config the app configuration. Not used by this filter.
     * @return true if all samples are flagged as valid, false otherwise
     */
	@Override
	public boolean process(List<FrameSample> samples, TimeCorrelationRunConfig config) throws MmtcException {
        if (samples.isEmpty()) {
            logger.warn("Sample Validity Filter failed: attempted to filter an empty sample set.");
            return false;
        }

        for (FrameSample sample : samples) {
            if (sample.getTkValid().equals(FrameSample.ValidState.UNSET)) {
                logger.warn("Sample Validity Filter failed: sample with ERT " + sample.getErtStr() +
                        " does not have a state set for its valid flag.");
                return false;
            }

            // Log if the sample is not valid.
            if (! sample.getTkValid().equals(FrameSample.ValidState.VALID)) {
                logger.warn("Sample Validity Filter failed: sample with ERT " + sample.getErtStr() +
                        " is marked invalid and discarded.");
                return false;
            }
        }

        return true;
	}
	
}
