package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class ConsecutiveMasterChannelFrameFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationRunConfig config) throws MmtcException {
        if (! (config.getMcfcMaxValue() > 0)) {
            throw new MmtcException("When using the ConsecutiveMasterChannelFrameFilter, the MCFC maximum value must be set in configuration to a positive integer.");
        }

        if (samples.isEmpty()) {
            logger.warn("ConsecutiveMasterChannelFrameFilter failed: attempted to filter an empty sample set.");
            return false;
        }

        for (int i = 0; i < samples.size(); i++) {
            final FrameSample current = samples.get(i);

            if (i > 0) {
                final FrameSample previous = samples.get(i-1);

                // issue a warning and continue if each pair of samples do not have directly incremental MCFC values
                final int expectedMcfc = (previous.getMcfc() + 1) % (config.getMcfcMaxValue() + 1);
                if (current.getMcfc() != expectedMcfc) {
                    logger.warn(
                            String.format(
                                    "ConsecutiveMasterChannelFrameFilter warning: %s and %s do not have sequential MCFC values.",
                                    getSampleDesc(previous),
                                    getSampleDesc(current)
                            )
                    );
                }
            }

            // fail the filter if the current sample's MCFC and supplemental MCFC do not have the expected relationship
            final int expectedSuppMcfc = (current.getMcfc() + config.getSupplementalSampleOffset()) % (config.getMcfcMaxValue() + 1);

            if (current.getSuppMcfc() == -1) {
                // the last sample in a frame sample set may not have a supplemental MCFC associated with it (i.e., if it's the last available frame in a contact, then there will be no supplemental frame to refer to)
                logger.warn(String.format("ConsecutiveMasterChannelFrameFilter cannot verify the MCFC of frame %d/%d due to a missing supplemental MCFC.  This could be due to the frame being the final frame in a sample set or contact, which is expected.  Frame:\n" + current.toString(), i, samples.size() - 1));
            } else if (current.getSuppMcfc() != expectedSuppMcfc) {
                logger.warn("ConsecutiveMasterChannelFrameFilter failed: The below sample and its supplemental sample do not have MCFCs separated by " +
                                        "the configured supplemental sample offset of " + config.getSupplementalSampleOffset() + ":\n" + current.toString());;
                return false;
            }
        }

        return true;
    }

    private static String getSampleDesc(FrameSample fs) {
        return String.format("the sample with ERT %s and MCFC %d", fs.getErtStr(), fs.getMcfc());
    }
}
