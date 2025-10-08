package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

import java.util.List;

/**
 * Interface that provides a common template for Filter classes.
 */
public interface TimeCorrelationFilter {
    static TimeCorrelationFilter createFilterInstanceByName(String name) throws MmtcException {
        switch (name) {
            case TimeCorrelationCliAppConfig.MIN_DATARATE_FILTER:
                return new MinDataRateFilter();
            case TimeCorrelationCliAppConfig.MAX_DATARATE_FILTER:
                return new MaxDataRateFilter();
            case TimeCorrelationCliAppConfig.ERT_FILTER:
                return new ErtFilter();
            case TimeCorrelationCliAppConfig.GROUND_STATION_FILTER:
                return new GroundStationFilter();
            case TimeCorrelationCliAppConfig.SCLK_FILTER:
                return new SclkFilter();
            case TimeCorrelationCliAppConfig.VALID_FILTER:
                return new ValidFilter();
            case TimeCorrelationCliAppConfig.CONSEC_FRAMES_FILTER:
                return new ConsecutiveFrameFilter();
            case TimeCorrelationCliAppConfig.VCID_FILTER:
                return new VcidFilter();
            case TimeCorrelationCliAppConfig.CONSEC_MC_FRAME_FILTER:
                return new ConsecutiveMasterChannelFrameFilter();
            default:
                throw new MmtcException("No such filter type: " + name);
        }
    }

    boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) throws MmtcException;
}
