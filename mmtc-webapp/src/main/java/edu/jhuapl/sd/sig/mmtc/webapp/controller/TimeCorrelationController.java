package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.TimeCorrelationWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.CorrelationPreviewService;
import io.javalin.Javalin;

import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

public class TimeCorrelationController extends BaseController {
    private final CorrelationPreviewService correlationPreviewService;

    public TimeCorrelationController(MmtcWebAppConfig config, CorrelationPreviewService correlationPreviewService) {
        super(config);
        this.correlationPreviewService = correlationPreviewService;
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        javalinApp.post("/api/v1/correlation/preview", ctx -> {
            TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig = ctx.bodyAsClass(TimeCorrelationWebAppConfig.CorrelationConfig.class);
            ctx.result(previewNewCorrelation(correlationConfig));
        });

        javalinApp.post("/api/v1/correlation/commit", ctx -> {
            final UUID previewId = UUID.fromString(ctx.queryParam("previewId"));
            ctx.result(commitNewCorrelationFromPreview(previewId));
        });

        /*
        javalinApp.post("/api/v1/correlation/create", ctx -> {
            TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig = ctx.bodyAsClass(TimeCorrelationWebAppConfig.CorrelationConfig.class);
            ctx.result(runNewCorrelation(correlationConfig));
        });
         */

        javalinApp.post("/api/v1/correlation/rollback", ctx -> {
            ctx.result(
                    rollback(ctx.queryParam("runId"))
            );
        });
    }

    private String commitNewCorrelationFromPreview(UUID previewId) throws Exception {
        try {
            CorrelationPreviewService.CorrelationPreview correlationPreview = correlationPreviewService.get(previewId);

            if (correlationPreview == null) {
                throw new IllegalStateException("No correlation preview found for ID " + previewId);
            }

            runNewCorrelation(correlationPreview.correlationConfig());
            correlationPreviewService.removeCorrelationPreview(previewId);

            return "success";
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    private String previewNewCorrelation(TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig) {
        try {
            // todo implement: run a correlation with a dry run but make an option to keep the resulting SCLK kernel in a temporary place for calculation use
            // also turn off other product generation except for SCLK kernel

            return correlationPreviewService.registerCorrelationPreview(correlationConfig, Paths.get("/tmp/to/be/populated")).toString();
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    public String runNewCorrelation(TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig) throws Exception {
        try {
            TimeCorrelationWebAppConfig timeCorrConfig = new TimeCorrelationWebAppConfig(config, correlationConfig);
            new TimeCorrelationApp(timeCorrConfig).run();
            return "success";
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    public String rollback(String toRunId) throws Exception {
        new TimeCorrelationRollback().rollback(Optional.of(toRunId));
        return "success";
    }
}
