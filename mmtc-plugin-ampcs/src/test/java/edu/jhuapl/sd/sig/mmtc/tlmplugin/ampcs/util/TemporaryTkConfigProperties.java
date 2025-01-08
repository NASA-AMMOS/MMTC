package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TemporaryTkConfigProperties implements AutoCloseable {
    private final Path tempDir;
    private final Path testTkConfigPath;

    public TemporaryTkConfigProperties(String baseConfigDir, Map<String, String> overrides) throws IOException {
        Path baseConfigPath = Paths.get(baseConfigDir, "TimeCorrelationConfigProperties.xml");

        List<String> lines = Files.readAllLines(baseConfigPath);

        this.tempDir = Files.createTempDirectory("mmtcTkConfig").toFile().toPath();
        this.testTkConfigPath = tempDir.resolve("TimeCorrelationConfigProperties.xml");

        List<String> newLines = lines.stream().map(l -> {

            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                final String key = entry.getKey();
                final String val = entry.getValue();


                if (l.contains(String.format("<entry key=\"%s\">", key))) {
                    return String.format("<entry key=\"%s\">%s</entry>", key, val);
                }
            }

            return l;
        }).collect(Collectors.toList());

        Files.write(
                testTkConfigPath,
                newLines
        );
    }

    public static TemporaryTkConfigProperties withTestTkPacketDescriptionFile(String baseConfigDir) throws IOException {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("telemetry.source.plugin.ampcs.tkpacket.tkPacketDescriptionFile.uri", "file://" + Paths.get("../mmtc-plugin-ampcs/src/test/resources/test_tk_pkt.xml").toAbsolutePath());
        return new TemporaryTkConfigProperties(baseConfigDir, overrides);
    }

    public Path getTestTkConfigDir() {
        return tempDir;
    }

    @Override
    public void close() throws Exception {
        Files.delete(testTkConfigPath);
        Files.delete(tempDir);
    }
}
