package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import io.javalin.Javalin;

public class MmtcApp {
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    private final Javalin javalinApp;

    public static void main(String[] args) {
        new MmtcApp().start();
    }

    public MmtcApp() {
        javalinApp = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add("/static");
        });

        // javalinApp.get("/", )

        javalinApp.get("/api/v1/version", ctx -> ctx.result("MMTC " + BUILD_INFO.toString()));
    }

    private void start() {
        javalinApp.start(7070);
    }
}
