package edu.jhuapl.sd.sig.mmtc.sandbox;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationXmlPropertiesConfig;
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

public class TimeCorrelationSandboxCreator {
    public static void createSandbox() throws Exception {
        final TimeCorrelationAppConfig currentConfig = new TimeCorrelationAppConfig();
        final Path currentMmtcHome = Paths.get(System.getenv("MMTC_HOME"));
        final Path sandboxedMmtcHome = Paths.get("/tmp/mmtc"); // fixme

        if (Files.exists(sandboxedMmtcHome)) {
            throw new MmtcException(String.format("The sandbox target path %s already exists; please remove the directory or file at this path or choose a different location to create your sandbox.", sandboxedMmtcHome));
        }

        if (! Files.isWritable(sandboxedMmtcHome)) {
            throw new MmtcException(String.format("Insufficient permissions to write new files at %s; please resolve or choose a different location to create your sandbox.", sandboxedMmtcHome));
        }

        // first, copy static resources
        for (String subDir : Arrays.asList("bin", "conf", "docs", "lib", "kernels")) {
            FileUtils.copyDirectory(
                    currentMmtcHome.resolve(subDir).toFile(),
                    sandboxedMmtcHome.resolve(subDir).toFile()
            );
        }

        final Path newConfigPath = sandboxedMmtcHome.resolve(TimeCorrelationXmlPropertiesConfig.TIME_COR_CONFIG_PROPERTIES_NAME);
        final Document newConfig = parseXml(newConfigPath);

        // next, create new log directory and set new logs to go there
        Files.createDirectory(sandboxedMmtcHome.resolve("log"));
        // todo find line that defines the RollingFile, and adjust its attributes to point to the sandbox area
        // <RollingFile name="mmtcLogFile" fileName="${env:MMTC_HOME}/log/mmtc.log" filePattern="${env:MMTC_HOME}/log/mmtc-%d{MM-dd-yy}-%i.log.gz">

        // next, create new output directory, copy current outputs into it

        // next, go through config file line-by-line and adjust paths to as necessary to point to new

        // tests to write:
        // - two NH correlations, then sandbox, then run the last two and make sure they match the regression dataset, and make sure original directory did not change at all
        // - three NH correlations, then sandbox, then roll back one, then run two and make sure they match the regression dataset, and make sure original directory did not change at all

        // last, write new config file
        writeXml(newConfig, newConfigPath);

        // echo new $MMTC_HOME location for new sandbox
    }

    private void setConfigEntryVal(final Document document, final String key, final String val) {
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

    private static Document parseXml(Path path) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static void writeXml(Document document, Path destinationPath) throws TransformerException, IOException {
        StringWriter writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        DOMSource source = new DOMSource(document);
        StringWriter strWriter = new StringWriter();
        StreamResult result = new StreamResult(strWriter);
        transformer.transform(source, result);
        Files.write(destinationPath, strWriter.getBuffer().toString().getBytes(StandardCharsets.UTF_8));
    }
}
