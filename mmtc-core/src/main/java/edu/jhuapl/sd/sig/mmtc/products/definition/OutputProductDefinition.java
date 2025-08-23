package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductLocation;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Defines common fields and methods common across all OutputProductDefinition implementations.
 *
 * @param <T> the type of ResolvedProductLocation that applies to the output product definition
 */
public abstract class OutputProductDefinition<T extends ResolvedProductLocation> {
    protected final String name;
    protected final Settable<Boolean> isBuiltIn = new Settable<>();

    protected OutputProductDefinition(String name) {
        this.name = name;
    }

    /**
     * @return the name of the instance of the output product definition; must be unique at MMTC runtime
     */
    public final String getName() {
        return this.name;
    }

    public final void setIsBuiltIn(boolean newIsBuiltInStatus) {
        isBuiltIn.set(newIsBuiltInStatus);
    }


    public final boolean isBuiltIn() {
        return isBuiltIn.get();
    }

    /**
     * 'Resolve' the location of the output product file(s) on disk for this definition.
     *
     * @param config the loaded MMTC configuration
     * @return the resolved product location, given MMTC's configuration
     * @throws MmtcException if any problem is encountered while determining the resolved product location
     */
    public abstract T resolveLocation(MmtcConfig config) throws MmtcException;

    /**
     * Where this product should be written, given the current time correlation
     *
     * @param context an object describing the time correlation inputs and results, possibly to be used in determining whether an output product should be written
     * @return true if the product should be written, false otherwise
     */
    public abstract boolean shouldBeWritten(TimeCorrelationContext context);

    /**
     * Write the product to the filesystem.  Called once per successful time correlation run.
     *
     * @param context an object describing the time correlation inputs and results, possibly to help inform the contents of the product
     * @return a ProductWriteResult containing the path to which updates were written as well as the new product 'version' (either a counter in the filename, or the new number of lines in the file)
     * @throws MmtcException if there is an issue in writing the output product to disk
     */
    public abstract ProductWriteResult write(TimeCorrelationContext context) throws MmtcException;

    /**
     * Assemble a ProductRollbackOperation describing the action to take in rolling back the version of the product output file(s) to an earlier version.
     *
     * @param config the loaded MMTC configuration
     * @param newLatestProductVersion the product version that will become the newest after the rollback operation completes
     * @return the assembled ProductRollbackOperation object describing the operation to execute
     * @throws MmtcException if there is an issue in calculating the rollback operation
     */
    public abstract TimeCorrelationRollback.ProductRollbackOperation<?> getRollbackOperation(RollbackConfig config, Optional<String> newLatestProductVersion) throws MmtcException;

    /**
     * Returns a map of configuration keys to update to new values during sandbox creation, given the original
     * MMTC configuration and the new directory where the MMTC sandbox will write further copies of this output product.
     *
     * Plugin-defined products are only permitted to update config keys with the prefix `product.plugin.[name].config.`
     *
     * @param originalConfig the original (non-sandboxed) MMTC configuration
     * @param newProductOutputDir the new directory where the MMTC sandbox will write further copies of this output product
     * @return a Map containing keys with values that should be changed for the new sandbox
     */
    public abstract Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir);

}
