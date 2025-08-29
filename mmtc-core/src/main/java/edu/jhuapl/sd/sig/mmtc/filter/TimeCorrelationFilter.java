package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

import java.util.List;

/**
 * Interface that provides a common template for Filter classes.
 */
public interface TimeCorrelationFilter {
    static TimeCorrelationFilter createFilterInstanceByName(String name) throws MmtcException {
        switch (name) {
            case TimeCorrelationRunConfig.MIN_DATARATE_FILTER:
                return new MinDataRateFilter();
            case TimeCorrelationRunConfig.MAX_DATARATE_FILTER:
                return new MaxDataRateFilter();
            case TimeCorrelationRunConfig.ERT_FILTER:
                return new ErtFilter();
            case TimeCorrelationRunConfig.GROUND_STATION_FILTER:
                return new GroundStationFilter();
            case TimeCorrelationRunConfig.SCLK_FILTER:
                return new SclkFilter();
            case TimeCorrelationRunConfig.VALID_FILTER:
                return new ValidFilter();
            case TimeCorrelationRunConfig.CONSEC_FRAMES_FILTER:
                return new ConsecutiveFrameFilter();
            case TimeCorrelationRunConfig.VCID_FILTER:
                return new VcidFilter();
            case TimeCorrelationRunConfig.CONSEC_MC_FRAME_FILTER:
                return new ConsecutiveMasterChannelFrameFilter();
            default:
                throw new MmtcException("No such filter type: " + name);
        }
    }

    boolean process(List<FrameSample> samples, TimeCorrelationRunConfig config) throws MmtcException;
}
