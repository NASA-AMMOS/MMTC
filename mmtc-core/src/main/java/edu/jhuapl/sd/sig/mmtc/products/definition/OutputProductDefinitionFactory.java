package edu.jhuapl.sd.sig.mmtc.products.definition;

import java.util.List;
import java.util.Map;

public interface OutputProductDefinitionFactory {
    // this is a list, not a set, to preserve stable ordering (for e.g. the Run History File's use)
    List<String> getApplicableTypes();

    OutputProductDefinition<?> create(String type, String name, Map<String, String> config);
}
