package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebAppConfig;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InfoController extends BaseController {
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    public InfoController(MmtcWebAppConfig config) {
        super(config);
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        javalinApp.get("/api/v1/info/version", ctx -> ctx.json(BUILD_INFO));
        javalinApp.get("/api/v1/info/configuration", ctx -> ctx.json(getConfigurationFileContent()));
    }

    public record ConfigurationFile(String filename, String contents) { }

    public List<ConfigurationFile> getConfigurationFileContent() throws IOException {
        final List<ConfigurationFile> configFiles = new ArrayList<>();

        configFiles.add(
                new ConfigurationFile(
                    "TimeCorrelationConfigProperties.xml",
                    String.join("\n", Files.readAllLines(this.config.getConfigFilepath()))
                )
        );

        final Path groundStationMapConfigPath = this.config.getGroundStationMapPath();
        configFiles.add(
                new ConfigurationFile(
                    groundStationMapConfigPath.getFileName().toString(),
                    String.join("\n", Files.readAllLines(groundStationMapConfigPath))
                )
        );

        final Path sclkPartitionMap = this.config.getSclkPartitionMapPath();
        configFiles.add(
                new ConfigurationFile(
                    sclkPartitionMap.getFileName().toString(),
                    String.join("\n", Files.readAllLines(sclkPartitionMap))
                )
        );

        return configFiles;
    }
}
