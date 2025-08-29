package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.CorrelationPreviewService;
import io.javalin.Javalin;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

public class TelemetryController extends BaseController {
    private final CorrelationPreviewService correlationPreviewService;

    public TelemetryController(MmtcWebAppConfig config, CorrelationPreviewService correlationPreviewService) {
        super(config);
        this.correlationPreviewService = correlationPreviewService;
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        javalinApp.get("/api/v1/telemetry/range", ctx -> {
            String beginTime = ctx.queryParam("beginTime");
            String endTime = ctx.queryParam("endTime");

            String sclkKernelName = ctx.queryParam("sclkKernelName");
            String correlationPreviewId = ctx.queryParam("correlationPreviewId");

            final Path sclkKernelPath;

            if (sclkKernelName != null && !sclkKernelName.isEmpty()) {
                EntireFileOutputProductDefinition sclkKernelDef = (EntireFileOutputProductDefinition) config.getOutputProductDefByName(SclkKernelProductDefinition.PRODUCT_NAME);
                sclkKernelPath = sclkKernelDef.resolveLocation(config).findMatchingFilename(sclkKernelName);
            } else if (correlationPreviewId != null && !correlationPreviewId.isEmpty()) {
                sclkKernelPath = correlationPreviewService.get(UUID.fromString(correlationPreviewId)).tempSclkKernelOnDisk();
            } else {
                throw new IllegalArgumentException("Must provide either sclkKernelName or correlationPreviewId");
            }

            ctx.json(getTelemetryPoints(beginTime, endTime, sclkKernelPath));
        });
    }

    record TimekeepingTelemetryPoint(
            FrameSample originalFrameSample,
            String scetUtc,
            double scetErrorMs
    ) { }

    private List<TimekeepingTelemetryPoint> getTelemetryPoints(String beginTimeUtcStr, String endTimeUtcStr, Path sclkKernelPath) throws Exception {
        List<FrameSample> frameSamples = config.getTelemetrySource().getSamplesInRange(
                    TimeConvert.parseIsoDoyUtcStr(beginTimeUtcStr),
                    TimeConvert.parseIsoDoyUtcStr(endTimeUtcStr)
                ).stream()
                .toList();

        Map<String, String> sclkKernelToLoad = new HashMap<>();
        sclkKernelToLoad.put(sclkKernelPath.toAbsolutePath().toString(), "sclk");

        synchronized(config.spiceMutex) {
            try {

                TimeConvert.loadSpiceKernels(config.getKernelsToLoad());
                // load specified sclkKernel
                TimeConvert.loadSpiceKernels(sclkKernelToLoad);
                return enrichFrameSamples(frameSamples);
            } finally {
                TimeConvert.unloadSpiceKernels();
            }
        }
    }

    private List<TimekeepingTelemetryPoint> enrichFrameSamples(List<FrameSample> frameSamples) throws Exception {
        // todo fixme, we need a configuration class here that has testmode and testmodeowlt and some methods but not the actual correlation targets
        TimeCorrelationRunConfig timeCorrelationRunConfig = new TimeCorrelationRunConfig((additionalTlmSrcOptionsByName -> {
            return new TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs(
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false
            );
        }), config);

        final List<TimekeepingTelemetryPoint> list = new ArrayList<>();

        for (FrameSample fs : frameSamples) {
            fs.computeAndSetTdBe(config.getFrameErtBitOffsetError());

            final TimeConvert.ScetMetrics fsScetMetrics = TimeConvert.calculateScetErrorNanos(timeCorrelationRunConfig, fs);

            list.add(
                    new TimekeepingTelemetryPoint(
                            fs,
                            TimeConvert.timeToIsoUtcString(fsScetMetrics.scetUtc),
                            new BigDecimal(fsScetMetrics.scetErrorNanos).divide(new BigDecimal(1_000_000.0)).doubleValue()
                    )
            );
        }

        return list;
    }
}
