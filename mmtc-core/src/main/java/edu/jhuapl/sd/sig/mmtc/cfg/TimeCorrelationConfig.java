package edu.jhuapl.sd.sig.mmtc.cfg;

import org.apache.commons.configuration2.FileBasedConfiguration;

import java.nio.file.Path;

/**
 * Parent class for TimeCorrelationXmlPropertiesConfig.
 */
abstract class TimeCorrelationConfig extends AbstractConfig {
    TimeCorrelationConfig(Path path) {
        super(path);
    }

    /**
     * @return the configuration object
     */
    abstract FileBasedConfiguration getConfig();
}
