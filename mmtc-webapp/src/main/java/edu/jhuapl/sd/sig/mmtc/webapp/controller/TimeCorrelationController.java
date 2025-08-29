package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfigInputSupplier;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkScetProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.CorrelationPreviewService;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
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
            // TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig = ctx.bodyAsClass(TimeCorrelationWebAppConfig.CorrelationConfig.class);
            ctx.result(previewNewCorrelation());
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

        /*
        javalinApp.get("/api/v1/correlation/lastCorrelationScetUtc", ctx -> {
            ctx.result(
                    getLastCorrelationScetUtc()
            );
        });


        javalinApp.get("/api/v1/correlation/defaultConfig", ctx -> {
                ctx.json(
                        getDefaultCorrelationConfig()
                );
        });


         */

        javalinApp.get("/api/v1/correlation/range", ctx -> {
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

            final Optional<OffsetDateTime> begin = beginTime == null ? Optional.empty() : Optional.of(TimeConvert.parseIsoDoyUtcStr(beginTime));
            final Optional<OffsetDateTime> end = endTime == null ? Optional.empty() : Optional.of(TimeConvert.parseIsoDoyUtcStr(endTime));

            final List<TimeCorrelationTriplet> allTriplets = getAllTimeCorrelationTriplets(sclkKernelPath.getFileName().toString());

            ctx.json(
                    allTriplets.stream()
                            .filter(t -> {
                                OffsetDateTime scetUtc = TimeConvert.parseIsoDoyUtcStr(t.scetUtc);

                                if (begin.isPresent()) {
                                    if (scetUtc.isBefore(begin.get())) {
                                        return false;
                                    }
                                }

                                if (end.isPresent()) {
                                    if (scetUtc.isAfter(end.get())) {
                                        return false;
                                    }
                                }

                                return true;
                            })
                            .collect(Collectors.toList())
            );
        });
    }

    private record DefaultCorrelationConfig (
        String mode,
        int samplesPerSet,
        boolean insertAdditionalSmoothingRecord,
        boolean createUpdateCommandFile
        ) { }

    /*
    private DefaultCorrelationConfig getDefaultCorrelationConfig() {
        return new DefaultCorrelationConfig(
                config.getDefaultCorrelationMode,
                config.getSamplesPerSet(),
                config.getSm
        )
    }

     */

    private String commitNewCorrelationFromPreview(UUID previewId) throws Exception {
        try {
            CorrelationPreviewService.CorrelationPreview correlationPreview = correlationPreviewService.get(previewId);

            if (correlationPreview == null) {
                throw new IllegalStateException("No correlation preview found for ID " + previewId);
            }

            runNewCorrelation();
            correlationPreviewService.removeCorrelationPreview(previewId);

            return "success";
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    private String previewNewCorrelation() {
        try {
            // todo implement: run a correlation with a dry run but make an option to keep the resulting SCLK kernel in a temporary place for calculation use
            // also turn off other product generation except for SCLK kernel

            return correlationPreviewService.registerCorrelationPreview(Paths.get("/tmp/to/be/populated")).toString();
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    public String runNewCorrelation() throws Exception {
        try {
            // todo take actual input from the call and populate this structure
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

            new TimeCorrelationApp(timeCorrelationRunConfig).run();
            return "success";
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    public String rollback(String toRunId) throws Exception {
        new TimeCorrelationRollback().rollback(Optional.of(toRunId));
        return "success";
    }

    public List<Map<String, String>> getRunHistoryContentsAsTableRows() throws MmtcException {
        final RunHistoryFile rhf = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());

        List<Map<String, String>> recs = rhf.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).stream()
                .map(tr -> tr.getColsAndVals())
                .collect(Collectors.toList());

        // to sort most recent rows first
        Collections.reverse(recs);

        return recs;
    }

    private record TimeCorrelationTriplet (
         String encSclk,
         String tdtG,
         String clkchgrate,
         String scetUtc
    ) { }

    private List<TimeCorrelationTriplet> getAllTimeCorrelationTriplets(String sclkKernelFilename) throws MmtcException {
        synchronized(config.spiceMutex) {
            try {
                TimeConvert.loadSpiceKernels(config.getKernelsToLoad());

                EntireFileOutputProductDefinition sclkKernelDef = (EntireFileOutputProductDefinition) config.getOutputProductDefByName(SclkKernelProductDefinition.PRODUCT_NAME);
                Path sclkKernelPath = sclkKernelDef.resolveLocation(config).findMatchingFilename(sclkKernelFilename);

                SclkKernel sclkKernel = new SclkKernel(sclkKernelPath.toAbsolutePath().toString());
                sclkKernel.readSourceProduct();

                // todo check that this is correct
                List<String[]> parsedRecords = sclkKernel.getParsedRecords();

                List<TimeCorrelationTriplet> results = new ArrayList<>();

                for (String[] rec : parsedRecords) {
                    results.add(
                            new TimeCorrelationTriplet(
                                    rec[SclkKernel.TRIPLET_ENCSCLK_FIELD_INDEX],
                                    rec[SclkKernel.TRIPLET_TDTG_FIELD_INDEX],
                                    rec[SclkKernel.TRIPLET_CLKCHGRATE_FIELD_INDEX],
                                    TimeConvert.tdtCalStrToUtc(rec[SclkKernel.TRIPLET_TDTG_FIELD_INDEX], 6)
                            )
                    );
                }

                return results;
            } catch (TextProductException | IOException | TimeConvertException e) {
                throw new MmtcException(e);
            } finally {
                TimeConvert.unloadSpiceKernels();
            }
        }
    }

    private String getLastCorrelationScetUtc() throws MmtcException {
        List<TimeCorrelationTriplet> allTimeCorrelationTriplets = getAllTimeCorrelationTriplets(config.getInputSclkKernelPath().getFileName().toString());
        return allTimeCorrelationTriplets.get(allTimeCorrelationTriplets.size() - 1).scetUtc;
    }
}
