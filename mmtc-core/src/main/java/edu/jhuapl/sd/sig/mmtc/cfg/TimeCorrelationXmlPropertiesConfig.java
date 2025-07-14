package edu.jhuapl.sd.sig.mmtc.cfg;

import org.apache.commons.configuration2.XMLPropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.BasePathLocationStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.jhuapl.sd.sig.mmtc.util.Environment;

import java.io.File;

/**
 * Provides access to the time correlation properties configuration file
 * parameters.
 */
public class TimeCorrelationXmlPropertiesConfig extends TimeCorrelationConfig {
    public static final String TIME_COR_CONFIG_PROPERTIES_NAME = "TimeCorrelationConfigProperties.xml";

    private FileBasedConfigurationBuilder<LocatedXMLPropertiesConfiguration> builder;

    private static final Logger logger = LogManager.getLogger();

    TimeCorrelationXmlPropertiesConfig() {
        // Do not set path; the file will be discovered based on filename in load()
        super(null);
    }

    /**
     * Load the contents of the configuration file into memory.
     *
     * @return true if successful, false otherwise
     */
    public boolean load() {
        try {
            String basePath = Environment.getEnvironmentVariable("TK_CONFIG_PATH");
            Parameters params = new Parameters();

            logger.info(String.format("Attempting to load configuration file %s from $TK_CONFIG_PATH (%s).", TIME_COR_CONFIG_PROPERTIES_NAME, basePath));
            builder = new FileBasedConfigurationBuilder<>(LocatedXMLPropertiesConfiguration.class)
                    .configure(params.properties()
                    .setBasePath(basePath)
                    .setFileName(TIME_COR_CONFIG_PROPERTIES_NAME)
                    .setLocationStrategy(new BasePathLocationStrategy())
                    .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));

            setPath(basePath + File.separator + TIME_COR_CONFIG_PROPERTIES_NAME);

            final LocatedXMLPropertiesConfiguration config = builder.getConfiguration();
            final boolean success = config != null;

            if (success) {
                String urlString = config.getURLString();
                logger.info("Loaded configuration from: " + urlString);
                setPath(urlString);
            }

            return success;
        } catch (ConfigurationException | NullPointerException ex) {
            logger.error("Error loading configuration file " + getPath(), ex);
            return false;
        }
    }

    /**
     * @return the properties configuration object
     */
    XMLPropertiesConfiguration getConfig() {
        try {
            return builder.getConfiguration();
        } catch (ConfigurationException ex) {
            logger.error("Error retrieving configuration.", ex);
        }

        return null;
    }
}
