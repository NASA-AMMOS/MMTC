package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import io.javalin.config.JavalinConfig;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseController {
    private static final ExecutorService singleThreadedExecutor = Executors.newSingleThreadExecutor();
    protected final MmtcWebAppConfig config;

    public BaseController(MmtcWebAppConfig config) {
        this.config = config;
    }

    public abstract void registerEndpoints(JavalinConfig javalinConfig);

    protected <T> T executeSingleThreaded(Callable<T> callable) throws ExecutionException, InterruptedException {
        return singleThreadedExecutor.submit(callable).get();
    }
}
