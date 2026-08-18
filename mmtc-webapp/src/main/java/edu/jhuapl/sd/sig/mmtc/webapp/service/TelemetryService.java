package edu.jhuapl.sd.sig.mmtc.webapp.service;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.TimekeepingAdjustmentParameters;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSampleMetrics;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class TelemetryService {

    private final MmtcWebAppConfig config;

    public TelemetryService(MmtcWebAppConfig config) {
        this.config = config;
    }

    public record TimekeepingTelemetryPoint(
            FrameSample originalFrameSample,
            double tdtG,
            String scetUtc,
            double scetErrorMs,
            double owltSec
    ) { }


    public synchronized List<TimekeepingTelemetryPoint> getTelemetryPoints(OffsetDateTime beginTimeErt, OffsetDateTime endTimeErt, Path sclkKernelPath) throws Exception {
        final List<FrameSample> frameSamples = config.getTelemetrySource().getSamplesInRange(beginTimeErt, endTimeErt);

        return config.withSpiceMutexAndKernels(sclkKernelPath, () -> {
            return enrichFrameSamples(frameSamples);
        });
    }

    private List<TimekeepingTelemetryPoint> enrichFrameSamples(List<FrameSample> frameSamples) throws Exception {
        TimekeepingAdjustmentParameters adjustmentParams = new TimekeepingAdjustmentParameters() {
            @Override
            public boolean isTestMode() {
                return config.isTestModeOwltEnabled();
            }

            @Override
            public double getTestModeOwlt() {
                return config.getTestModeOwltSec();
            }
        };

        final List<TimekeepingTelemetryPoint> list = new ArrayList<>();

        for (FrameSample fs : frameSamples) {
            fs.computeAndSetTdBe(config.getFrameErtBitOffsetError());

            final FrameSampleMetrics fsMetrics = TimeConvert.calculateFrameSampleMetrics(config, adjustmentParams, fs);

            list.add(
                    new TimekeepingTelemetryPoint(
                            fs,
                            fsMetrics.tdtG,
                            TimeConvert.timeToIsoUtcString(fsMetrics.scetUtc),
                            fsMetrics.getScetErrorMs(),
                            fsMetrics.owltSec
                    )
            );
        }

        return list;
    }
}
