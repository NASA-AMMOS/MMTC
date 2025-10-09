package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import io.javalin.Javalin;

public class InfoController extends BaseController {
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    public InfoController(MmtcWebAppConfig config) {
        super(config);
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        javalinApp.get("/api/v1/info/version", ctx -> ctx.json(BUILD_INFO));
    }
}
