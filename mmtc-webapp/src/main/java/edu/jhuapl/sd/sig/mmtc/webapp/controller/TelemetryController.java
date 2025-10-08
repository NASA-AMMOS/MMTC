package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.CorrelationPreviewService;
import io.javalin.Javalin;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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

            ctx.json(getSamplesInRange(beginTime, endTime, sclkKernelPath));
        });
    }

    record EnrichedFrameSample(
            FrameSample frameSample
    ) { }

    private List<EnrichedFrameSample> getSamplesInRange(String beginTimeUtcStr, String endTimeUtcStr, Path sclkKernelPath) throws MmtcException {
        return config.getTelemetrySource().getSamplesInRange(
                    TimeConvert.parseIsoDoyUtcStr(beginTimeUtcStr),
                    TimeConvert.parseIsoDoyUtcStr(endTimeUtcStr)
                ).stream()
                .map(fs -> new EnrichedFrameSample(fs))
                .toList();
    }
}
