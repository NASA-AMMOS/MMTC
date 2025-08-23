package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class PluginProvidedProductConfig {
    public final Path pluginDir;
    public final String pluginJarPrefix;
    public final String outputProductType;
    public final String outputProductName;
    public final boolean enabled;
    public final Map<String, String> config;

    public PluginProvidedProductConfig(Path pluginDir, String pluginJarPrefix, String outputProductType, String outputProductName, boolean enabled, Map<String, String> config) {
        this.pluginDir = pluginDir;
        this.pluginJarPrefix = pluginJarPrefix;
        this.outputProductType = outputProductType;
        this.outputProductName = outputProductName;
        this.enabled = enabled;
        this.config = config;
    }

    public static PluginProvidedProductConfig createFrom(MmtcConfig config, String productName) throws MmtcException {
        final String baseKey = "product.plugin." + productName;

        final Path pluginDir = Paths.get(config.getEnsureNotEmpty(baseKey + ".pluginDirectory"));
        final String pluginJarPrefix = config.getEnsureNotEmpty(baseKey + ".pluginJarPrefix");
        final String outputProductType = config.getEnsureNotEmpty(baseKey + ".outputProductType");
        final boolean enabled = config.getBoolean(baseKey + ".enabled");

        final Map<String, String> outputProductConfig = new HashMap<>();
        final String productConfigKeyPrefix = baseKey + ".config";
        for (String productConfigKey : config.getKeysWithPrefix(productConfigKeyPrefix)) {
            outputProductConfig.put(
                    productConfigKey.replace(productConfigKeyPrefix + ".", ""),
                    config.getString(productConfigKey)
            );
        }

        return new PluginProvidedProductConfig(
                pluginDir,
                pluginJarPrefix,
                outputProductType,
                productName,
                enabled,
                outputProductConfig
        );
    }

    @Override
    public String toString() {
        return "PluginProvidedProductConfig{" +
                "pluginDir=" + pluginDir +
                ", pluginJarPrefix='" + pluginJarPrefix + '\'' +
                ", outputProductType='" + outputProductType + '\'' +
                ", outputProductName='" + outputProductName + '\'' +
                ", enabled=" + enabled +
                ", config=" + config +
                '}';
    }
}
