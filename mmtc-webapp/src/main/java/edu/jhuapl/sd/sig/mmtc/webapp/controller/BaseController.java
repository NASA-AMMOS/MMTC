package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import io.javalin.Javalin;

public abstract class BaseController {
    protected final MmtcWebAppConfig config;

    public BaseController(MmtcWebAppConfig config) {
        this.config = config;
    }

    public abstract void registerEndpoints(Javalin javalinApp);
}
