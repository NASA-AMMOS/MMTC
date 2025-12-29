package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.AncillaryInfo;
import edu.jhuapl.sd.sig.mmtc.correlation.CorrelationInfo;
import edu.jhuapl.sd.sig.mmtc.correlation.GeometryInfo;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.config.NewTimeCorrelationConfigRequest;
import edu.jhuapl.sd.sig.mmtc.webapp.config.NewTimeCorrelationConfigRequestPreview;
import edu.jhuapl.sd.sig.mmtc.webapp.service.OutputProductService;
import edu.jhuapl.sd.sig.mmtc.webapp.service.TelemetryService;
import io.javalin.Javalin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TimeCorrelationController extends BaseController {
    private static final Logger logger = LogManager.getLogger();

    private final TelemetryService telemetryService;
    private final OutputProductService outputProductService;

    public TimeCorrelationController(MmtcWebAppConfig config, TelemetryService telemetryService, OutputProductService outputProductService) {
        super(config);
        this.telemetryService = telemetryService;
        this.outputProductService = outputProductService;
    }

    record CorrelationResults (
            CorrelationInfo correlation,
            GeometryInfo geometry,
            AncillaryInfo ancillary,
            OffsetDateTime appRunTime,
            List<String> warnings
    ) {
        public static CorrelationResults from(TimeCorrelationContext ctx) {
            return new CorrelationResults(
                    ctx.correlation,
                    ctx.geometry,
                    ctx.ancillary,
                    ctx.appRunTime,
                    ctx.getWarnings()
            );
        }
    }

    record TimeCorrelationPreviewResults (
            List<TimeCorrelationTriplet> updatedTriplets,
            List<TelemetryService.TimekeepingTelemetryPoint> telemetryPoints,
            CorrelationResults correlationResults
    ) { }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        javalinApp.post("/api/v1/correlation/preview", ctx -> {
            NewTimeCorrelationConfigRequestPreview correlationConfigPreview = ctx.bodyAsClass(NewTimeCorrelationConfigRequestPreview.class);
            ctx.json(previewNewCorrelation(correlationConfigPreview));
        });

        javalinApp.post("/api/v1/correlation/create", ctx -> {
            NewTimeCorrelationConfigRequest correlationConfig = ctx.bodyAsClass(NewTimeCorrelationConfigRequest.class);
            ctx.json(createNewCorrelation(correlationConfig));
        });

        javalinApp.get("/api/v1/correlation/runhistory", ctx -> {
            ctx.json(getRunHistoryContentsAsTableRows());
        });

        javalinApp.post("/api/v1/correlation/rollback", ctx -> {
            ctx.result(rollback(ctx.queryParam("runId")));
        });

        javalinApp.get("/api/v1/correlation/defaultConfig", ctx -> {
            ctx.json(getNewCorrelationConfig());
        });

        javalinApp.get("/api/v1/correlation/range", ctx -> {
            String beginTime = ctx.queryParam("beginTime");
            String endTime = ctx.queryParam("endTime");
            String sclkKernelName = ctx.queryParam("sclkKernelName");
            ctx.json(getCorrelationTriplets(beginTime, endTime, sclkKernelName));
        });
    }

    private List<TimeCorrelationTriplet> getCorrelationTriplets(String beginTime, String endTime, String sclkKernelName) throws MmtcException, IOException {
        final Path sclkKernelPath;

        if (sclkKernelName == null || sclkKernelName.isEmpty()) {
            throw new IllegalArgumentException("Must provide SCLK kernel name");
        }

        EntireFileOutputProductDefinition sclkKernelDef = (EntireFileOutputProductDefinition) config.getOutputProductDefByName(SclkKernelProductDefinition.PRODUCT_NAME);
        sclkKernelPath = sclkKernelDef.resolveLocation(config).findMatchingFilename(sclkKernelName);

        final Optional<OffsetDateTime> begin = beginTime == null ? Optional.empty() : Optional.of(TimeConvert.parseIsoDoyUtcStr(beginTime));
        final Optional<OffsetDateTime> end = endTime == null ? Optional.empty() : Optional.of(TimeConvert.parseIsoDoyUtcStr(endTime));

        final List<TimeCorrelationTriplet> allTriplets = getAllTimeCorrelationTriplets(sclkKernelPath.getFileName().toString());

        return allTriplets.stream()
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
                .collect(Collectors.toList());
    }

    private TimeCorrelationPreviewResults previewNewCorrelation(NewTimeCorrelationConfigRequestPreview correlationConfigPreview) throws Exception {
        Files.createDirectories(Paths.get("/tmp/mmtc/previews"));
        Path tmpSclkKernelPath = Paths.get("/tmp/mmtc/previews/", String.format("mmtc_sclk_preview_%s.tsc", UUID.randomUUID().toString()));
        correlationConfigPreview.setDryRunConfig(new TimeCorrelationRunConfig.DryRunConfig(
                TimeCorrelationRunConfig.DryRunMode.DRY_RUN_GENERATE_SEPARATE_SCLK_ONLY,
                tmpSclkKernelPath
        ));

        // have this preview endpoint calculate and return the graph data, among other stats about the new correlation run
        try {
            final TimeCorrelationContext ctxResult = runNewCorrelation(correlationConfigPreview);
            final List<TelemetryService.TimekeepingTelemetryPoint> tlmPoints = telemetryService.getTelemetryPoints(correlationConfigPreview.beginTimeErt, correlationConfigPreview.endTimeErt, tmpSclkKernelPath);

            final List<TimeCorrelationTriplet> updatedTriplets = new ArrayList<>();
            config.withSpiceKernels(tmpSclkKernelPath, () -> {
                if (ctxResult.correlation.updatedInterpolatedTriplet.isSet()) {
                    updatedTriplets.add(convertTriplet(ctxResult.correlation.updatedInterpolatedTriplet.get()));
                }

                if (ctxResult.correlation.newSmoothingTriplet.isSet()) {
                    updatedTriplets.add(convertTriplet(ctxResult.correlation.newSmoothingTriplet.get()));
                }

                if (ctxResult.correlation.newPredictedTriplet.isSet()) {
                    updatedTriplets.add(convertTriplet(ctxResult.correlation.newPredictedTriplet.get()));
                }

                return null;
            });

            return new TimeCorrelationPreviewResults(
                    updatedTriplets,
                    tlmPoints,
                    CorrelationResults.from(ctxResult)
            );
        } catch (Exception e) {
            logger.error(e);
            throw e;
        } finally {
            if (Files.exists(tmpSclkKernelPath)) {
                Files.delete(tmpSclkKernelPath);
            }
        }
    }

    public static class NewTimeCorrelationConfigRequestTemplate extends NewTimeCorrelationConfigRequest {
        public final int samplesPerSet;
        public final Double newCorrelationMinTdt;
        public final Double predictedClkRateMinLookbackHours;
        public final Double predictedClkRateMaxLookbackHours;

        public NewTimeCorrelationConfigRequestTemplate(NewTimeCorrelationConfigRequest corrConfig, int samplesPerSet, Double newCorrelationMinTdt, Double predictedClkRateMinLookbackHours, Double predictedClkRateMaxLookbackHours) {
            super(corrConfig);
            this.samplesPerSet = samplesPerSet;
            this.newCorrelationMinTdt = newCorrelationMinTdt;
            this.predictedClkRateMinLookbackHours = predictedClkRateMinLookbackHours;
            this.predictedClkRateMaxLookbackHours = predictedClkRateMaxLookbackHours;
        }
    }

    private NewTimeCorrelationConfigRequest getNewCorrelationConfig() throws MmtcException, IOException {
        String sclkKernelFilename = outputProductService.getLatestFilenameForDef(outputProductService.getSclkKernelOutputProductDef()).get();
        final List<TimeCorrelationTriplet> allTimeCorrelationTriplets = getAllTimeCorrelationTriplets(sclkKernelFilename);
        final TimeCorrelationTriplet latestTimeCorrelationTriplet = allTimeCorrelationTriplets.get(allTimeCorrelationTriplets.size() - 1);

        // next correlation's min TDT is that of the last current triplet, plus 1ms
        final Double newCorrelationMinTdt = config.withSpiceKernels(
                config.getInputSclkKernelPath(),
                () -> TimeConvert.tdtCalStrToTdt(latestTimeCorrelationTriplet.tdtG.replace("@", "")) + .001
        );

        final NewTimeCorrelationConfigRequestTemplate defaultCorrConfig = new NewTimeCorrelationConfigRequestTemplate(
                new NewTimeCorrelationConfigRequest(),
                config.getSamplesPerSet(),
                newCorrelationMinTdt,
                config.getPredictedClkRateLookBackHours(),
                config.getMaxPredictedClkRateLookBackHours()
        );

        defaultCorrConfig.setTestModeOwltEnabled(false);
        defaultCorrConfig.setTestModeOwltSec(0.0);
        defaultCorrConfig.setClockChangeRateConfig(new TimeCorrelationRunConfig.ClockChangeRateConfig(config.getConfiguredClockChangeRateMode(), 1.0));
        defaultCorrConfig.setDryRunConfig(new TimeCorrelationRunConfig.DryRunConfig(TimeCorrelationRunConfig.DryRunMode.NOT_DRY_RUN, null));
        defaultCorrConfig.setAdditionalSmoothingRecordConfigOverride(
                new TimeCorrelationRunConfig.AdditionalSmoothingRecordConfig(
                        config.isAdditionalSmoothingCorrelationRecordInsertionEnabled(),
                        config.getAdditionalSmoothingCorrelationRecordInsertionCoarseSclkTickDuration()
                )
        );

        defaultCorrConfig.setDisableContactFilter(false);
        defaultCorrConfig.setCreateUplinkCmdFile(false);

        return defaultCorrConfig;
    }

    private CorrelationResults createNewCorrelation(NewTimeCorrelationConfigRequest newCorrConfig) throws Exception {
        return CorrelationResults.from(runNewCorrelation(newCorrConfig));
    }

    private TimeCorrelationContext runNewCorrelation(NewTimeCorrelationConfigRequest newCorrConfig) throws Exception {
        try {
            return new TimeCorrelationApp(new TimeCorrelationRunConfig(newCorrConfig, config)).run();
        } finally {
            TimeConvert.unloadSpiceKernels();
        }
    }

    private String rollback(String toRunId) throws Exception {
        new TimeCorrelationRollback().rollback(Optional.of(toRunId));
        return "success";
    }

    private List<Map<String, String>> getRunHistoryContentsAsTableRows() throws MmtcException {
        final RunHistoryFile rhf = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());

        List<Map<String, String>> recs = rhf.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).stream()
                .map(TableRecord::getColsAndVals)
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

    private TimeCorrelationTriplet convertTriplet(SclkKernel.CorrelationTriplet t) throws TimeConvertException {
        return new TimeCorrelationTriplet(
                Double.toString(t.encSclk),
                t.tdtStr,
                Double.toString(t.clkChgRate),
                TimeConvert.tdtCalStrToUtc(t.tdtStr, 6)
        );
    }

    private List<TimeCorrelationTriplet> getAllTimeCorrelationTriplets(String sclkKernelFilename) throws MmtcException, IOException {
        final Path sclkKernelPath = config.getSclkKernelPathFor(sclkKernelFilename);

        return config.withSpiceKernels(sclkKernelPath, () -> {
            SclkKernel sclkKernel = new SclkKernel(sclkKernelPath.toAbsolutePath().toString());
            sclkKernel.readSourceProduct();

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
        });
    }
}
