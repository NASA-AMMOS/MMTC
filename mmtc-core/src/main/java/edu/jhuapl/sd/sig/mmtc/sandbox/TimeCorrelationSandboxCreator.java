package edu.jhuapl.sd.sig.mmtc.sandbox;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationXmlPropertiesConfig;
import edu.jhuapl.sd.sig.mmtc.products.SclkKernel;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TimeCorrelationSandboxCreator {
    private final Path originalMmtcHomeDir;
    private final Path originalTkConfigDir;
    private final TimeCorrelationAppConfig originalTkConfig;

    private final Path sandboxedMmtcHomeDir;
    private Path sandboxedTkConfDir;
    private Path sandboxedTkConfigPath;
    private Document sandboxedTkConfig;

    public TimeCorrelationSandboxCreator(Path newSandboxPath) throws Exception {
        this.originalMmtcHomeDir = Paths.get(System.getenv("MMTC_HOME"));
        this.originalTkConfigDir = Paths.get(System.getenv("TK_CONFIG_PATH"));
        this.originalTkConfig = new TimeCorrelationAppConfig();

        this.sandboxedMmtcHomeDir = newSandboxPath;
    }

    public void create() throws Exception {
        // Create sandbox directory
        if (Files.exists(sandboxedMmtcHomeDir)) {
            throw new MmtcException(String.format("The sandbox target path %s already exists; please remove the directory or file at this path or choose a different location to create your sandbox.", sandboxedMmtcHomeDir));
        }

        if (! Files.isWritable(sandboxedMmtcHomeDir)) {
            throw new MmtcException(String.format("Insufficient permissions to write new files at %s; please resolve or choose a different location to create your sandbox.", sandboxedMmtcHomeDir));
        }

        Files.createDirectories(sandboxedMmtcHomeDir);

        // Copy MMTC start script, libs, log conf, and docs (if present)
        copyProgramDirs();
        copyAndTransformLogConfiguration();
        copyDocsDir();

        // Create a new copy of MMTC's configuration directory and contents
        sandboxedTkConfDir = sandboxedMmtcHomeDir.resolve("conf");
        Files.createDirectory(sandboxedTkConfDir);
        sandboxedTkConfigPath = sandboxedTkConfDir.resolve(TimeCorrelationXmlPropertiesConfig.TIME_COR_CONFIG_PROPERTIES_FILENAME);
        Files.copy(
                originalTkConfigDir.resolve(TimeCorrelationXmlPropertiesConfig.TIME_COR_CONFIG_PROPERTIES_FILENAME),
                sandboxedTkConfigPath
        );
        sandboxedTkConfig = parseXml(sandboxedTkConfigPath);
        copySecondaryConfigurationFiles();

        copyOutputs();

        // Write new config file
        writeXml(sandboxedTkConfig, sandboxedTkConfigPath);

        // todo echo new $MMTC_HOME and $TK_CONFIG_PATH values for new sandbox

        // todo tests to write:
        // - two NH correlations, then sandbox, then run the last two and make sure they match the regression dataset, and make sure original directory did not change at all
        // - three NH correlations, then sandbox, then roll back one, then run two and make sure they match the regression dataset, and make sure original directory did not change at all
        // - a EURC correlation, then sandbox, then run another and make sure it matches regression dataset
    }

    private void copyProgramDirs() throws IOException {
        // copy required static resources for MMTC software
        for (String reqdSubDir : Arrays.asList("bin", "lib")) {
            FileUtils.copyDirectory(
                    originalMmtcHomeDir.resolve(reqdSubDir).toFile(),
                    sandboxedMmtcHomeDir.resolve(reqdSubDir).toFile()
            );
        }
    }

    private void copyDocsDir() throws IOException {
        if (Files.exists(originalMmtcHomeDir.resolve("docs"))) {
            FileUtils.copyDirectory(
                    originalMmtcHomeDir.resolve("docs").toFile(),
                    sandboxedMmtcHomeDir.resolve("docs").toFile()
            );
        }
    }

    private void copySecondaryConfigurationFiles() throws IOException {
        // SCLK partition map
        final Path newSclkPartitionMapPath = sandboxedTkConfDir.resolve(originalTkConfig.getSclkPartitionMapPath().getFileName());
        Files.copy(
                originalTkConfig.getSclkPartitionMapPath(),
                newSclkPartitionMapPath
        );
        setConfigEntryVal(sandboxedTkConfig, "sclkPartitionMap.path", newSclkPartitionMapPath.toString());

        // Ground Station map
        final Path newGroundStationMapPath = sandboxedTkConfDir.resolve(originalTkConfig.getGroundStationMapPath().getFileName());
        Files.copy(
                originalTkConfig.getSclkPartitionMapPath(),
                newGroundStationMapPath
        );
        setConfigEntryVal(sandboxedTkConfig, "groundStationMap.path", newGroundStationMapPath.toString());

        // Telemetry source plugins
        final Path newTlmSourcePluginDir = Files.createDirectory(sandboxedMmtcHomeDir.resolve(Paths.get("lib", "plugins")));
        List<Path> pluginJarsToCopy = Files.list(originalTkConfig.getTelemetrySourcePluginDirectory()).filter(p -> p.startsWith(originalTkConfig.getTelemetrySourcePluginJarPrefix())).collect(Collectors.toList());
        for (Path pluginJar : pluginJarsToCopy) {
            Files.copy(pluginJar, newTlmSourcePluginDir.resolve(pluginJar.getFileName()));
        }
        setConfigEntryVal(sandboxedTkConfig, "telemetry.source.pluginDirectory", newTlmSourcePluginDir.toString());

        // todo call a telemetry source method to clone its configuration to a new directory (and pass in new Document for it to be able to modify it)

        // Copy the metakernel, if it's specified
        if (originalTkConfig.containsNonEmptyKey("spice.kernel.mk.path")) {
            Files.createDirectory(sandboxedMmtcHomeDir.resolve("kernels"));

            Files.copy(
                    Paths.get(originalTkConfig.getString("spice.kernel.mk.path")),
                    sandboxedMmtcHomeDir.resolve(
                            Paths.get("kernels", Paths.get(originalTkConfig.getString("spice.kernel.mk.path")).getFileName().toString())
                    )
            );
        }
    }

    private void copyAndTransformLogConfiguration() throws IOException {
        // create new log directory and set new logs to go there
        Files.createDirectory(sandboxedMmtcHomeDir.resolve("log"));
        // todo find line that defines the RollingFile, and adjust its attributes to point to the sandbox area
        // <RollingFile name="mmtcLogFile" fileName="${env:MMTC_HOME}/log/mmtc.log" filePattern="${env:MMTC_HOME}/log/mmtc-%d{MM-dd-yy}-%i.log.gz">

    }

    private void copyOutputs() throws IOException {
        final Path newOutputDir = sandboxedMmtcHomeDir.resolve("output");

        // SCLK kernels
        final Path newSclkOutputDir = newOutputDir.resolve("sclk");
        Files.createDirectory(newSclkOutputDir);
        List<Path> sclkKernelsToCopy = Files.list(originalTkConfig.getSclkKernelOutputDir())
                .filter(p -> p.startsWith(originalTkConfig.getSclkKernelBasename()))
                .filter(p -> p.endsWith(SclkKernel.FILE_SUFFIX))
                .collect(Collectors.toList());
        for (Path sclkKernel : sclkKernelsToCopy) {
            Files.copy(
                    sclkKernel,
                    newSclkOutputDir.resolve(sclkKernel.getFileName())
            );
        }
        setConfigEntryVal(sandboxedTkConfig, "spice.kernel.sclk.kerneldir", newSclkOutputDir.toString());

        // Run History file
        final Path newRunHistoryFilePath = newOutputDir.resolve(originalTkConfig.getRunHistoryFilePath().getFileName());
        Files.copy(
                originalTkConfig.getRunHistoryFilePath(),
                newRunHistoryFilePath
        );
        setConfigEntryVal(sandboxedTkConfig, "table.runHistoryFile.path", newRunHistoryFilePath.toString());

        // Raw Telemetry Table
        final Path newRawTelemetryTablePath = newOutputDir.resolve(originalTkConfig.getRawTelemetryTablePath().getFileName());
        Files.copy(
                originalTkConfig.getRawTelemetryTablePath(),
                newRawTelemetryTablePath
        );
        setConfigEntryVal(sandboxedTkConfig, "table.rawTelemetryTable.path", newRawTelemetryTablePath.toString());

        // Time History File
        final Path newTimeHistoryFilePath = newOutputDir.resolve(originalTkConfig.getTimeHistoryFilePath().getFileName());
        Files.copy(
                originalTkConfig.getTimeHistoryFilePath(),
                newTimeHistoryFilePath
        );
        setConfigEntryVal(sandboxedTkConfig, "table.timeHistoryFile.path", newTimeHistoryFilePath.toString());

        // SCLK-SCET files
        // todo rebase off existing PR to get access to better methods to look at this config

        // uplink cmd files

    }

    private static void setConfigEntryVal(final Document document, final String key, final String val) {
        // Select the correct config key and set the value in loaded document
        NodeList entryNodes = document.getElementsByTagName("entry");

        boolean keyFound = false;
        for (int i = 0; i < entryNodes.getLength(); i++) {
            Node entryNode = entryNodes.item(i);
            if (entryNode.getNodeType() == Node.ELEMENT_NODE) {
                Element entryElement = (Element) entryNode;
                String entryKey = entryElement.getAttribute("key");
                if (entryKey.equals(key)) {
                    entryElement.setTextContent(val);
                    keyFound = true;
                }
            }
        }

        if (! keyFound) {
            Element newEntry = document.createElement("entry");
            newEntry.setAttribute("key", key);
            newEntry.setTextContent(val);
            document.getElementsByTagName("properties").item(0).appendChild(newEntry);
        }
    }

    private static Document parseXml(Path path) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return factory.newDocumentBuilder().parse(path.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
    }

    private static void writeXml(Document document, Path destinationPath) throws IOException {
        try {
            StringWriter writer = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            DOMSource source = new DOMSource(document);
            StringWriter strWriter = new StringWriter();
            StreamResult result = new StreamResult(strWriter);
            transformer.transform(source, result);
            Files.write(destinationPath, strWriter.getBuffer().toString().getBytes(StandardCharsets.UTF_8));
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }
}
