package edu.jhuapl.sd.sig.mmtc.sandbox;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcSandboxCreatorConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationXmlPropertiesConfig;
import edu.jhuapl.sd.sig.mmtc.products.SclkKernel;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.util.Map;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class MmtcSandboxCreator {
    private static final Logger logger = LogManager.getLogger();

    private final MmtcSandboxCreatorConfig config;

    private final Path originalMmtcHomeDir;
    private final Path originalTkConfigDir;
    private final TimeCorrelationAppConfig originalTkConfig;

    private final Path sandboxedMmtcHomeDir;
    private Path sandboxedTkConfDir;
    private Path sandboxedTkConfigPath;
    private Document sandboxedTkConfig;

    public MmtcSandboxCreator(String... args) throws Exception {
        this.config = new MmtcSandboxCreatorConfig(args);

        this.originalMmtcHomeDir = Paths.get(System.getenv("MMTC_HOME"));
        this.originalTkConfigDir = Paths.get(System.getenv("TK_CONFIG_PATH"));
        this.originalTkConfig = new TimeCorrelationAppConfig();

        this.sandboxedMmtcHomeDir = config.getNewSandboxPath();
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
        copyLoggerConfiguration();
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
        logger.info(USER_NOTICE, "New MMTC sandbox created at: " + sandboxedMmtcHomeDir.toAbsolutePath());
        logger.info(USER_NOTICE, "Before using, be sure to do one of the following: either unset $MMTC_HOME and $TK_CONFIG_PATH, or set them to reference the new sandbox location, e.g.:");
        logger.info(USER_NOTICE, "export MMTC_HOME=" + sandboxedMmtcHomeDir.toAbsolutePath());
        logger.info(USER_NOTICE, "export TK_CONFIG_PATH=" + sandboxedTkConfigPath.toAbsolutePath());

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
        {
            final Path newSclkPartitionMapPath = sandboxedTkConfDir.resolve(originalTkConfig.getSclkPartitionMapPath().getFileName());
            Files.copy(
                    originalTkConfig.getSclkPartitionMapPath(),
                    newSclkPartitionMapPath
            );
            setConfigEntryVal(sandboxedTkConfig, "sclkPartitionMap.path", newSclkPartitionMapPath.toString());
        }

        // Ground Station map
        {
            final Path newGroundStationMapPath = sandboxedTkConfDir.resolve(originalTkConfig.getGroundStationMapPath().getFileName());
            Files.copy(
                    originalTkConfig.getSclkPartitionMapPath(),
                    newGroundStationMapPath
            );
            setConfigEntryVal(sandboxedTkConfig, "groundStationMap.path", newGroundStationMapPath.toString());
        }

        // Telemetry source plugins
        {
            final Path newTlmSourcePluginDir = Files.createDirectory(sandboxedMmtcHomeDir.resolve(Paths.get("lib", "plugins")));
            List<Path> pluginJarsToCopy = Files.list(originalTkConfig.getTelemetrySourcePluginDirectory()).filter(p -> p.startsWith(originalTkConfig.getTelemetrySourcePluginJarPrefix())).collect(Collectors.toList());
            for (Path pluginJar : pluginJarsToCopy) {
                Files.copy(pluginJar, newTlmSourcePluginDir.resolve(pluginJar.getFileName()));
            }
            setConfigEntryVal(sandboxedTkConfig, "telemetry.source.pluginDirectory", newTlmSourcePluginDir.toString());

            Map<String, String> configKeysToUpdate = config.getTelemetrySource().sandboxTelemetrySourceConfiguration(config, sandboxedMmtcHomeDir, sandboxedTkConfigPath);
            for (Map.Entry<String, String> updatedKeyVal : configKeysToUpdate.entrySet()) {
                final String key = updatedKeyVal.getKey();
                final String val = updatedKeyVal.getValue();

                if (!key.startsWith("telemetry.source.plugin.")) {
                    throw new IOException(String.format("Invalid config key update requested by plugin: %s=%s", key, val));
                }

                setConfigEntryVal(sandboxedTkConfig, key, val);
            }
        }

        // Copy the metakernel, if it's specified, as MMTC-specific metakernels should be considered part of MMTC-managed configuration
        {
            if (originalTkConfig.containsNonEmptyKey("spice.kernel.mk.path")) {
                Files.createDirectory(sandboxedMmtcHomeDir.resolve("kernels"));

                Path newMetakernelLocation = sandboxedMmtcHomeDir.resolve(
                        Paths.get("kernels", Paths.get(originalTkConfig.getString("spice.kernel.mk.path")).getFileName().toString())
                );
                Files.copy(
                        Paths.get(originalTkConfig.getString("spice.kernel.mk.path")),
                        newMetakernelLocation
                );
                setConfigEntryVal(sandboxedTkConfig, "spice.kernel.mk.path", newMetakernelLocation.toString());
            }
        }

        // other kernels that are referenced are unlikely to be MMTC-specific, so just update their paths to absolute ones to ensure they can be read from the new sandbox location
        {
            // don't need to handle SCLK kernel here; those are read from the output directory
            // don't need to handle metakernel, as we copied that above

            // there should only be one lsk
            if (originalTkConfig.containsKey("spice.kernel.lsk.path")) {
                setConfigEntryVal(
                        sandboxedTkConfig,
                        "spice.kernel.lsk.path",
                        Paths.get(originalTkConfig.getString("spice.kernel.lsk.path")).toAbsolutePath().toString()
                );
            }

            // preserving order for kernels is critical, as order is meaningful to the SPICE kernel furnishing process
            List<String> kernelTypesForPathUpdates = Arrays.asList(
                    "spk",
                    "pck",
                    "fk"
            );

            for (String kernelType : kernelTypesForPathUpdates) {
                final String key = String.format("spice.kernel.%s.path", kernelType);
                String[] kernelArr = originalTkConfig.getStringArray(key);

                String kernelsWithAbsolutePaths = Arrays.stream(kernelArr)
                            .map(kernel -> Paths.get(kernel).toAbsolutePath().toString())
                            .collect(Collectors.joining("\n"));

                setConfigEntryVal(
                        sandboxedTkConfig,
                        key,
                        kernelsWithAbsolutePaths
                );
            }
        }
    }

    private void copyLoggerConfiguration() throws IOException {
        // create new log directory and set new logs to go there
        Files.createDirectory(sandboxedMmtcHomeDir.resolve("log"));

        if (Files.exists(originalTkConfigDir.resolve("log4j2.xml"))) {
            Files.copy(
                    originalTkConfigDir.resolve("log4j2.xml"),
                    sandboxedTkConfDir.resolve("log4j2.xml")
            );
        } else {
            logger.info(String.format("Could not locate log4j2.xml; copying default logger configuration to %s.  Please configure as desired.", sandboxedTkConfDir.resolve("log4j2.xml")));
            Files.copy(
                MmtcSandboxCreator.class.getResourceAsStream("log4j2.xml"),
                sandboxedTkConfDir.resolve("log4j2.xml")
            );
        }
    }

    private void copyOutputs() throws IOException {
        final Path newOutputDir = sandboxedMmtcHomeDir.resolve("output");

        // SCLK kernels
        {
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
        }

        // Run History file
        {
            final Path newRunHistoryFilePath = newOutputDir.resolve(originalTkConfig.getRunHistoryFilePath().getFileName());
            Files.copy(
                    originalTkConfig.getRunHistoryFilePath(),
                    newRunHistoryFilePath
            );
            setConfigEntryVal(sandboxedTkConfig, "table.runHistoryFile.path", newRunHistoryFilePath.toString());
        }

        // Raw Telemetry Table
        {
            final Path newRawTelemetryTablePath = newOutputDir.resolve(originalTkConfig.getRawTelemetryTablePath().getFileName());
            Files.copy(
                    originalTkConfig.getRawTelemetryTablePath(),
                    newRawTelemetryTablePath
            );
            setConfigEntryVal(sandboxedTkConfig, "table.rawTelemetryTable.path", newRawTelemetryTablePath.toString());
        }

        // Time History File
        {
            final Path newTimeHistoryFilePath = newOutputDir.resolve(originalTkConfig.getTimeHistoryFilePath().getFileName());
            Files.copy(
                    originalTkConfig.getTimeHistoryFilePath(),
                    newTimeHistoryFilePath
            );
            setConfigEntryVal(sandboxedTkConfig, "table.timeHistoryFile.path", newTimeHistoryFilePath.toString());
        }

        // SCLK-SCET files
        {
            // todo change this to see if any sclk-scet files have ever been produced
            if (originalTkConfig.createSclkScetFile()) {
                final Path newSclkScetOutputDir = newOutputDir.resolve("sclkscet");
                Files.createDirectory(newSclkScetOutputDir);
                List<Path> sclkScetFilesToCopy = Files.list(originalTkConfig.getSclkScetOutputDir())
                        .filter(p -> p.startsWith(originalTkConfig.getSclkScetFileBasename()))
                        .filter(p -> p.endsWith(originalTkConfig.getSclkScetFileSuffix()))
                        .collect(Collectors.toList());
                for (Path sclkScetFile : sclkScetFilesToCopy) {
                    Files.copy(
                            sclkScetFile,
                            newSclkScetOutputDir.resolve(sclkScetFile.getFileName())
                    );
                }
                setConfigEntryVal(sandboxedTkConfig, "product.sclkScetFile.dir", newSclkScetOutputDir.toString());
            }
        }

        // uplink cmd files
        {
            // todo change this to see if any uplink command files have ever been produced
            if (originalTkConfig.isCreateUplinkCmdFile()) {
                final Path newUplinkCmdFileOutputDir = newOutputDir.resolve("uplinkCmd");
                Files.createDirectory(newUplinkCmdFileOutputDir);
                List<Path> uplinkCmdFilesToCopy = Files.list(Paths.get(originalTkConfig.getUplinkCmdFileDir()))
                        .filter(p -> p.startsWith(originalTkConfig.getUplinkCmdFileBasename()))
                        .collect(Collectors.toList());
                for (Path uplinkCmdFile : uplinkCmdFilesToCopy) {
                    Files.copy(
                            uplinkCmdFile,
                            newUplinkCmdFileOutputDir.resolve(uplinkCmdFile.getFileName())
                    );
                }
                setConfigEntryVal(sandboxedTkConfig, "product.uplinkCmdFile.outputDir", newUplinkCmdFileOutputDir.toString());
            }
        }
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
