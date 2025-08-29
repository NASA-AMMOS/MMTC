package edu.jhuapl.sd.sig.mmtc.products.definition.util;

import java.nio.file.Path;

public class ResolvedProductDirPrefixSuffix implements ResolvedProductLocation {
    public final Path containingDirectory;
    public final String filenamePrefix;
    public final String filenameSuffix;

    public ResolvedProductDirPrefixSuffix(Path containingDirectory, String filenamePrefix, String filenameSuffix) {
        this.containingDirectory = containingDirectory;
        this.filenamePrefix = filenamePrefix;
        this.filenameSuffix = filenameSuffix;
    }
}
