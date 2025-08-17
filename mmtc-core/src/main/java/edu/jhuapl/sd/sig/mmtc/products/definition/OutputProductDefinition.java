package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.AbstractTimeCorrelationTable;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public abstract class OutputProductDefinition<T extends OutputProductDefinition.ResolvedProductLocation> {
    public static List<OutputProductDefinition<?>> all() {
        List<OutputProductDefinition<?>> all = new ArrayList<>(Arrays.asList(
                new SclkKernelProductDefinition(),
                new SclkScetProductDefinition(),
                new TimeHistoryFileProductDefinition(),
                new RawTlmTableProductDefinition(),
                new UplinkCommandFileProductDefinition()
        ));

        // ensure product definitions provide unique names
        if (all.stream().map(def -> def.name).collect(Collectors.toSet()).size() != all.size()) {
            throw new IllegalStateException("Please check your loaded configuration and/or plugins to ensure all output products have unique names.");
        }

        return Collections.unmodifiableList(all);
    }

    public final String name;

    public OutputProductDefinition(String name) {
        this.name = name;
    }

    public abstract T resolveLocation(RollbackConfig config) throws MmtcException;

    public abstract ProductWriteResult write(TimeCorrelationContext context) throws MmtcException;

    public abstract TimeCorrelationRollback.ProductRollbackOperation<?> getRollbackOperation(RollbackConfig config, Optional<String> newLatestProductVersion) throws MmtcException;

    public abstract boolean shouldBeWritten(TimeCorrelationContext context);

    public interface ResolvedProductLocation {
    }

    public static class ResolvedProductDirAndPrefix implements ResolvedProductLocation {
        public final Path containingDirectory;
        public final String filenamePrefix;

        public ResolvedProductDirAndPrefix(Path containingDirectory, String filenamePrefix) {
            this.containingDirectory = containingDirectory;
            this.filenamePrefix = filenamePrefix;
        }
    }

    public static class ResolvedProductPath implements ResolvedProductLocation {
        public final Path pathToProduct;
        public final AbstractTimeCorrelationTable table;

        public ResolvedProductPath(Path pathToProduct, AbstractTimeCorrelationTable table) {
            this.pathToProduct = pathToProduct;
            this.table = table;
        }
    }

    public static class ProductWriteResult {
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
}
