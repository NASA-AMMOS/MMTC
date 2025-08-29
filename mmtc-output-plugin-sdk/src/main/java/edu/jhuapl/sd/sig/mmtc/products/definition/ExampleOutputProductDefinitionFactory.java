package edu.jhuapl.sd.sig.mmtc.products.definition;

import java.util.*;

public class ExampleOutputProductDefinitionFactory implements OutputProductDefinitionFactory {
    private static final String EXAMPLE_ENTIRE_FILE_TYPE = "Example Entire File Product";
    private static final String EXAMPLE_APPENDED_FILE_TYPE = "Example Appended File Product";

    @Override
    public List<String> getApplicableTypes() {
        return Arrays.asList(EXAMPLE_ENTIRE_FILE_TYPE, EXAMPLE_APPENDED_FILE_TYPE);
    }

    @Override
    public OutputProductDefinition<?> create(String type, String name, Map<String, String> config) {
        if (type.equals(EXAMPLE_ENTIRE_FILE_TYPE)) {
            return new ExampleEntireFileOutputProductDefinition(name, config);
        } else if (type.equals(EXAMPLE_APPENDED_FILE_TYPE)) {
            return new ExampleAppendedFileOutputProductDefinition(name, config);
        } else {
            throw new IllegalArgumentException("Can't create product of type: " + type);
        }
    }
}