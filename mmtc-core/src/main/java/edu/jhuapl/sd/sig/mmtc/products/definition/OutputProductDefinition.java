package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * A class that implements this interface represents a type of output product that MMTC can operate one, and each
 * instance of such a class represents a single use of that type of output product.  A single use of an output product
 * type may involve a single file that is appended, to or a set of files that grows, with each successful time correlation run.
 *
 * @param <T> the type of ResolvedProductLocation that applies to the output product definition
 */
public interface OutputProductDefinition<T extends BaseOutputProductDefinition.ResolvedProductLocation> {
    /**
     * @return the name of the instance of the output product definition; must be unique at MMTC runtime
     */
    String getName();

    /**
     * 'Resolve' the location of the output product file(s) on disk for this definition.
     *
     * @param config the loaded MMTC configuration
     * @return the resolved product location, given MMTC's configuration
     * @throws MmtcException if any problem is encountered while determining the resolved product location
     */
    T resolveLocation(MmtcConfig config) throws MmtcException;

    /**
     * Where this product should be written, given the current time correlation
     *
     * @param context an object describing the time correlation inputs and results, possibly to be used in determining whether an output product should be written
     * @return true if the product should be written, false otherwise
     */
    boolean shouldBeWritten(TimeCorrelationContext context);

    /**
     * Write the product to the filesystem.  Called once per successful time correlation run.
     *
     * @param context an object describing the time correlation inputs and results, possibly to help inform the contents of the product
     * @return a ProductWriteResult containing the path to which updates were written as well as the new product 'version' (either a counter in the filename, or the new number of lines in the file)
     * @throws MmtcException if there is an issue in writing the output product to disk
     */
    BaseOutputProductDefinition.ProductWriteResult write(TimeCorrelationContext context) throws MmtcException;

    /**
     * Assemble a ProductRollbackOperation describing the action to take in rolling back the version of the product output file(s) to an earlier version.
     *
     * @param config the loaded MMTC configuration
     * @param newLatestProductVersion the product version that will become the newest after the rollback operation completes
     * @return the assembled ProductRollbackOperation object describing the operation to execute
     * @throws MmtcException if there is an issue in calculating the rollback operation
     */
    TimeCorrelationRollback.ProductRollbackOperation<?> getRollbackOperation(RollbackConfig config, Optional<String> newLatestProductVersion) throws MmtcException;

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
    Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir);

    /**
     * Should be called only by mmtc-core
     * @param newIsBuiltInStatus whether this product definition is built-in or provided by a plugin
     */
    void setIsBuiltIn(boolean newIsBuiltInStatus);

    /**
     * @return whether the product definition is built-in to MMTC or was provided by a plugin
     */
    boolean isBuiltIn();
}
