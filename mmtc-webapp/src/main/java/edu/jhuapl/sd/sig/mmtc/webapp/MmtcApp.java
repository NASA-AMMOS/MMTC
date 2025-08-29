package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import io.javalin.Javalin;

public class MmtcApp {
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    public static void main(String[] args) {
        Javalin.create()
                .get("/", ctx -> ctx.result("Hello World!  You are running MMTC " + BUILD_INFO.toString()))
                .start(7070);
    }t
}
