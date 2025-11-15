package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.TimeCorrelationWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.CorrelationPreviewService;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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

        javalinApp.get("/api/v1/correlation/runhistory", ctx -> {
            ctx.json(
                    getRunHistoryContentsAsTableRows()
            );
        });

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

    public List<Map<String, String>> getRunHistoryContentsAsTableRows() throws IOException, MmtcException {
        final RunHistoryFile rhf = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());

        List<Map<String, String>> recs = rhf.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).stream()
                .map(tr -> tr.getColsAndVals())
                .collect(Collectors.toList());

        // to sort most recent rows first
        Collections.reverse(recs);

        return recs;
    }
}
