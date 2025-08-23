package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.nio.file.Path;

/**
 * Contains common fields, types, and logic common across all OutputProductDefinition implementations.
 *
 * @param <T> the type of ResolvedProductLocation that applies to the output product definition
 */
public abstract class BaseOutputProductDefinition<T extends BaseOutputProductDefinition.ResolvedProductLocation> implements OutputProductDefinition<T> {
    protected final String name;
    protected final Settable<Boolean> isBuiltIn = new Settable<>();

    public BaseOutputProductDefinition(String name) {
        this.name = name;
    }

    @Override
    public final String getName() {
        return this.name;
    }

    @Override
    public final void setIsBuiltIn(boolean newIsBuiltInStatus) {
        isBuiltIn.set(newIsBuiltInStatus);
    }

    @Override
    public final boolean isBuiltIn() {
        return isBuiltIn.get();
    }

    public interface ResolvedProductLocation {
    }

    public static class ResolvedProductDirPrefixSuffix implements ResolvedProductLocation {
        public final Path containingDirectory;
        public final String filenamePrefix;
        public final String filenameSuffix;

        public ResolvedProductDirPrefixSuffix(Path containingDirectory, String filenamePrefix, String filenameSuffix) {
            this.containingDirectory = containingDirectory;
            this.filenamePrefix = filenamePrefix;
            this.filenameSuffix = filenameSuffix;
        }
    }

    public static class ResolvedProductPath implements ResolvedProductLocation {
        public final Path pathToProduct;

        public ResolvedProductPath(Path pathToProduct) {
            this.pathToProduct = pathToProduct;
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
