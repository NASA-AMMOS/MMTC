package edu.jhuapl.sd.sig.mmtc.webapp.service;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationMetricsConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelemetryService {

    private final MmtcWebAppConfig config;

    public TelemetryService(MmtcWebAppConfig config) {
        this.config = config;
    }

    public record TimekeepingTelemetryPoint(
            FrameSample originalFrameSample,
            double tdtG,
            String scetUtc,
            double scetErrorMs
    ) { }


    public List<TimekeepingTelemetryPoint> getTelemetryPoints(OffsetDateTime beginTime, OffsetDateTime endTime, Path sclkKernelPath) throws Exception {
        final List<FrameSample> frameSamples = config.getTelemetrySource().getSamplesInRange(beginTime, endTime);

        final Map<String, String> sclkKernelToLoad = new HashMap<>();
        sclkKernelToLoad.put(sclkKernelPath.toAbsolutePath().toString(), "sclk");

        return config.withSpiceMutexAndDefaultKernels(() -> {
            // load specified sclkKernel atop already-specified SCLK kernel
            TimeConvert.loadSpiceKernels(sclkKernelToLoad);
            return enrichFrameSamples(frameSamples);
        });
    }

    private List<TimekeepingTelemetryPoint> enrichFrameSamples(List<FrameSample> frameSamples) throws Exception {
        TimeCorrelationMetricsConfig metricsConfig = new TimeCorrelationMetricsConfig() {
            @Override
            public double getFrameErtBitOffsetError() {
                return config.getFrameErtBitOffsetError();
            }

            @Override
            public Integer getTkSclkFineTickModulus() throws TimeConvertException {
                return config.getTkSclkFineTickModulus();
            }

            @Override
            public int getNaifSpacecraftId() {
                return config.getNaifSpacecraftId();
            }

            // todo should we be carrying forward the input test mode situation here?  i don't think so, because we don't want to apply an even one to all points
            @Override
            public boolean isTestMode() {
                return false;
            }

            @Override
            public double getTestModeOwlt() {
                return 0;
            }

            @Override
            public String getStationId(int pathId) throws MmtcException {
                return config.getStationId(pathId);
            }

            @Override
            public int getSclkPartition(OffsetDateTime groundReceiptTime) {
                return config.getSclkPartition(groundReceiptTime);
            }

            @Override
            public double getSpacecraftTimeDelaySec() {
                return config.getSpacecraftTimeDelaySec();
            }
        };

        final List<TimekeepingTelemetryPoint> list = new ArrayList<>();

        for (FrameSample fs : frameSamples) {
            fs.computeAndSetTdBe(metricsConfig.getFrameErtBitOffsetError());

            final TimeConvert.FrameSampleMetrics fsMetrics = TimeConvert.calculateScetErrorNanos(metricsConfig, fs);

            list.add(
                    new TimekeepingTelemetryPoint(
                            fs,
                            fsMetrics.tdtG,
                            TimeConvert.timeToIsoUtcString(fsMetrics.scetUtc),
                            new BigDecimal(fsMetrics.scetErrorNanos).divide(new BigDecimal(1_000_000.0)).doubleValue()
                    )
            );
        }

        return list;
    }
}
