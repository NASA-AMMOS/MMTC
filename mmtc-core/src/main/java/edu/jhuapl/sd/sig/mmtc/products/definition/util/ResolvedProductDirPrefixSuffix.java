package edu.jhuapl.sd.sig.mmtc.products.definition.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ResolvedProductDirPrefixSuffix implements ResolvedProductLocation {
    public final Path containingDirectory;
    public final String filenamePrefix;
    public final String filenameSuffix;

    public ResolvedProductDirPrefixSuffix(Path containingDirectory, String filenamePrefix, String filenameSuffix) {
        this.containingDirectory = containingDirectory;
        this.filenamePrefix = filenamePrefix;
        this.filenameSuffix = filenameSuffix;
    }

    public List<Path> findAllMatching() throws IOException {
        return Files.list(containingDirectory)
                .filter(p -> p.getFileName().toString().startsWith(filenamePrefix))
                .filter(p -> p.getFileName().toString().endsWith(filenameSuffix))
                .collect(Collectors.toList());
    }
}
