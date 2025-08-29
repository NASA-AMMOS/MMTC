package edu.jhuapl.sd.sig.mmtc.products.definition.util;

import java.nio.file.Path;

public class ProductWriteResult {
    public final Path path;
    public final String newVersion;

    public ProductWriteResult(Path path, String newVersion) {
        this.path = path;
        this.newVersion = newVersion;
    }

    public ProductWriteResult(Path path, int newVersion) {
        this(path, Integer.toString(newVersion));
    }
}
