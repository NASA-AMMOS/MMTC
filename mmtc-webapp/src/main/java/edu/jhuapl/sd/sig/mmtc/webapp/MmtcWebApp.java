package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.products.util.BuiltInOutputProductMigrationManager;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.AuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.AutoGenBasicHttpAuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.auth.NoopAuthorizationService;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.*;
import edu.jhuapl.sd.sig.mmtc.webapp.service.OutputProductService;
import edu.jhuapl.sd.sig.mmtc.webapp.service.TelemetryService;
import edu.jhuapl.sd.sig.mmtc.webapp.util.MmtcObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;

public class MmtcWebApp {
    private static final Logger logger = LogManager.getLogger();

    private final Javalin javalinApp;
    private final MmtcWebAppConfig config;

    private final TelemetryService telemetryService;
    private final OutputProductService outputProductService;

    public static void main(String[] args) throws Exception {
        new MmtcWebApp().start();
    }

    public MmtcWebApp() throws Exception {
        this.config = new MmtcWebAppConfig();

        new BuiltInOutputProductMigrationManager(config).assertExistingProductsDoNotRequireMigration();

        TimeConvert.loadSpiceLib();

        // todo disconnect tlm source on shutdown
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
            javalinConfig.staticFiles.add("/docs");

            javalinConfig.jsonMapper(new JavalinJackson(MmtcObjectMapper.get(), false));
        });

        // set up auth
        final AuthorizationService authService = switch(config.getAuthMode()) {
            case NONE                    -> new NoopAuthorizationService();
            case AUTOGEN_BASIC_HTTP_AUTH -> new AutoGenBasicHttpAuthorizationService(config);
        };

        javalinApp.before(ctx -> {
            authService.ensureAuthorized(ctx);
        });
        logger.info("Auth service: " + authService.getClass().getSimpleName());

        this.telemetryService = new TelemetryService(config);
        this.outputProductService = new OutputProductService(config);

        // instantiate controllers and set up routes
        Collection<BaseController> controllers = new HashSet<>();
        controllers.add(new TimeCorrelationController(config, this.telemetryService, this.outputProductService));
        controllers.add(new TelemetryController(config, this.telemetryService));
        controllers.add(new OutputProductController(config, this.outputProductService));
        controllers.add(new InfoController(config));
        controllers.forEach(c -> c.registerEndpoints(javalinApp));

        javalinApp.exception(Exception.class, (e, ctx) -> {
            logger.error("Server error", e);
            ctx.status(500);

            String errorMessage = Optional.ofNullable(e.getMessage()).orElse("An error occurred.").trim();

            if (! errorMessage.endsWith(".")) {
                errorMessage += ".";
            }
            errorMessage += " Please see the MMTC log for details.";

            ctx.result(errorMessage);
        });
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
