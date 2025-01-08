package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

import java.util.List;

/**
 * Interface that provides a common template for Filter classes.
 */
public interface TimeCorrelationFilter {
    static TimeCorrelationFilter createFilterInstanceByName(String name) throws MmtcException {
        switch (name) {
            case TimeCorrelationAppConfig.MIN_DATARATE_FILTER:
                return new MinDataRateFilter();
            case TimeCorrelationAppConfig.MAX_DATARATE_FILTER:
                return new MaxDataRateFilter();
            case TimeCorrelationAppConfig.ERT_FILTER:
                return new ErtFilter();
            case TimeCorrelationAppConfig.GROUND_STATION_FILTER:
                return new GroundStationFilter();
            case TimeCorrelationAppConfig.SCLK_FILTER:
                return new SclkFilter();
            case TimeCorrelationAppConfig.VALID_FILTER:
                return new ValidFilter();
            case TimeCorrelationAppConfig.CONSEC_FRAMES_FILTER:
                return new ConsecutiveFrameFilter();
            case TimeCorrelationAppConfig.VCID_FILTER:
                return new VcidFilter();
            case TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER:
                return new ConsecutiveMasterChannelFrameFilter();
            default:
                throw new MmtcException("No such filter type: " + name);
        }
    }

    boolean process(List<FrameSample> samples, TimeCorrelationAppConfig config) throws MmtcException;
}
