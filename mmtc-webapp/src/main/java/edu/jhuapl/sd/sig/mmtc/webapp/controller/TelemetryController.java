package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.TelemetryService;
import io.javalin.Javalin;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

public class TelemetryController extends BaseController {
    private final TelemetryService telemetryService;

    public TelemetryController(MmtcWebAppConfig config, TelemetryService telemetryService) {
        super(config);
        this.telemetryService = telemetryService;
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        javalinApp.get("/api/v1/telemetry/range", ctx -> {
            OffsetDateTime beginTime = TimeConvert.parseIsoDoyUtcStr(ctx.queryParam("beginTime"));
            OffsetDateTime endTime = TimeConvert.parseIsoDoyUtcStr(ctx.queryParam("endTime"));

            String sclkKernelName = ctx.queryParam("sclkKernelName");

            final Path sclkKernelPath;

            if (sclkKernelName == null || sclkKernelName.isEmpty()) {
                throw new IllegalArgumentException("Must provide SCLK kernel name");
            }

            EntireFileOutputProductDefinition sclkKernelDef = (EntireFileOutputProductDefinition) config.getOutputProductDefByName(SclkKernelProductDefinition.PRODUCT_NAME);
            sclkKernelPath = sclkKernelDef.resolveLocation(config).findMatchingFilename(sclkKernelName);

            ctx.json(telemetryService.getTelemetryPoints(beginTime, endTime, sclkKernelPath));
        });
    }

}
