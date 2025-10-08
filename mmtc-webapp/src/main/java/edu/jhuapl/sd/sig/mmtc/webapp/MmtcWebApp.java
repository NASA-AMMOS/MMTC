package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.AuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.AutoGenBasicHttpAuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.NoopAuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.TimeCorrelationController;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.BaseController;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.OutputProductController;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.TelemetryController;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.Collection;
import java.util.HashSet;

public class MmtcWebApp {
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    private static final Logger logger = LogManager.getLogger();

    private final Javalin javalinApp;
    private final MmtcWebAppConfig config;

    public static void main(String[] args) throws Exception {
        new MmtcWebApp().start();
    }

    public MmtcWebApp() throws Exception {
        this.config = new MmtcWebAppConfig();

        // todo do migration assertion check

        javalinApp = Javalin.create(javalinConfig -> {
            if (config.isPlaintextServerEnabled()) {
                javalinConfig.jetty.addConnector((server, httpConfiguration) -> {
                    ServerConnector connector = new ServerConnector(server);
                    connector.setPort(config.getPlaintextServerPort());
                    return connector;
                });

                logger.info("MMTC web app listening via plaintext on port " + config.getPlaintextServerPort());
            }

            if (config.isTlsServerEnabled()) {
                javalinConfig.jetty.addConnector((server, httpConfiguration) -> {
                    ServerConnector sslConnector = new ServerConnector(server, getSslContextFactory());
                    sslConnector.setPort(config.getTlsServerPort());
                    return sslConnector;
                });

                logger.info("MMTC web app listening via TLS on port " + config.getTlsServerPort());
            }

            javalinConfig.staticFiles.add("/static");
        });

        // set up auth
        final AuthorizationService authService = switch(config.getAuthMode()) {
            case NONE                    -> new NoopAuthorizationService();
            case AUTOGEN_BASIC_HTTP_AUTH -> new AutoGenBasicHttpAuthorizationService();
        };


        javalinApp.beforeMatched(ctx -> {
            if (! authService.isAuthorized(ctx)) {
                throw new UnauthorizedResponse();
            }
        });
        logger.info("Auth service: " + authService.getClass().getSimpleName());

        // instantiate controllers and set up routes
        Collection<BaseController> controllers = new HashSet<>();
        controllers.add(new TimeCorrelationController(config));
        controllers.add(new TelemetryController(config));
        controllers.add(new OutputProductController(config));
        controllers.forEach(c -> c.registerEndpoints(javalinApp));

        javalinApp.get("/api/v1/info/version", ctx -> ctx.result("MMTC " + BUILD_INFO.toString()));
    }

    private SslContextFactory.Server getSslContextFactory() {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(config.getString("webapp.tls.keystore.location"));
        sslContextFactory.setKeyStorePassword("webapp.tls.keystore.password");
        return sslContextFactory;
    }

    private void start() {
        javalinApp.start();
    }
}
