package edu.jhuapl.sd.sig.mmtc.cfg;

import org.apache.commons.configuration2.FileBasedConfiguration;

/**
 * Parent class for TimeCorrelationXmlPropertiesConfig.
 */
abstract class TimeCorrelationConfig extends AbstractConfig {
    TimeCorrelationConfig(String path) {
        super(path);
    }

    /**
     * @return the configuration object
     */
    abstract FileBasedConfiguration getConfig();
}
