package edu.jhuapl.sd.sig.mmtc.products.definition.util;

import java.nio.file.Path;

public class ResolvedProductPath implements ResolvedProductLocation {
    public final Path pathToProduct;

    public ResolvedProductPath(Path pathToProduct) {
        this.pathToProduct = pathToProduct;
    }
}
