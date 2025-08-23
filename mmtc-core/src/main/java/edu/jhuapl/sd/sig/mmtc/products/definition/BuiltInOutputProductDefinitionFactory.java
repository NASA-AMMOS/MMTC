package edu.jhuapl.sd.sig.mmtc.products.definition;

import java.util.*;

public class BuiltInOutputProductDefinitionFactory implements OutputProductDefinitionFactory {
    public static final List<String> BUILT_IN_PRODUCT_TYPES = Arrays.asList("SCLK Kernel", "SCLK-SCET File", "Time History File", "Raw Telemetry Table", "Uplink Command File");

    @Override
    public List<String> getApplicableTypes() {
        return new ArrayList<>(BUILT_IN_PRODUCT_TYPES);
    }

    @Override
    public OutputProductDefinition<?> create(String type, String name, Map<String, String> config) {
        // unlike plugins, we assume these to be 'singleton' output products (in that the given name does not matter, and they will always be assigned the default 'name' of the built-in instance of this product)
        // (this will change if MMTC is ever enhanced to e.g. maintain two separate SCLK kernel lineages)
        // as such, they are not provided a name parameter
        // also, as another exemption, they do not read a config object, and instead read their configuration directly from a TimeCorrelationAppConfig as they were made before the current convention (though again, this may be standardized to the same pattern as the plugin outputs)

        switch(type) {
            case "SCLK Kernel": return new SclkKernelProductDefinition();
            case "SCLK-SCET File": return new SclkScetProductDefinition();
            case "Raw Telemetry Table": return new RawTlmTableProductDefinition();
            case "Time History File": return new TimeHistoryFileProductDefinition();
            case "Uplink Command File": return new UplinkCommandFileProductDefinition();
            default:
                throw new IllegalArgumentException("No such built-in product type: " + type);
        }
    }
}
