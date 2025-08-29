package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.AuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.AutoGenBasicHttpAuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.NoopAuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.*;
import edu.jhuapl.sd.sig.mmtc.webapp.service.CorrelationPreviewService;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.Collection;
import java.util.HashSet;

public class MmtcWebApp {
    private static final Logger logger = LogManager.getLogger();

    private final Javalin javalinApp;
    private final MmtcWebAppConfig config;

    private final CorrelationPreviewService correlationPreviewService;

    public static void main(String[] args) throws Exception {
        new MmtcWebApp().start();
    }

    public MmtcWebApp() throws Exception {
        this.config = new MmtcWebAppConfig();

        TimeConvert.loadSpiceLib();

        // todo do migration assertion check

        // todo also disconnect on shutdown
        this.config.getTelemetrySource().connect();

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

        this.correlationPreviewService = new CorrelationPreviewService();

        // instantiate controllers and set up routes
        Collection<BaseController> controllers = new HashSet<>();
        controllers.add(new TimeCorrelationController(config, this.correlationPreviewService));
        controllers.add(new TelemetryController(config, this.correlationPreviewService));
        controllers.add(new OutputProductController(config));
        controllers.add(new InfoController(config));
        controllers.forEach(c -> c.registerEndpoints(javalinApp));
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
