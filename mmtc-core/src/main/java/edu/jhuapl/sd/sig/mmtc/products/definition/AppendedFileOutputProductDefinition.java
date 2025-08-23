package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

/**
 * Defines an output product that is represented by a single text file, which has one or many lines appended to it with each successful time correlation.
 */
public abstract class AppendedFileOutputProductDefinition extends BaseOutputProductDefinition<BaseOutputProductDefinition.ResolvedProductPath> {
    private static final Logger logger = LogManager.getLogger();

    public AppendedFileOutputProductDefinition(String name) {
        super(name);
    }

    @Override
    public final ProductWriteResult write(TimeCorrelationContext ctx) throws MmtcException {
        final ProductWriteResult pwr = appendToProduct(ctx);
        logger.info(USER_NOTICE, String.format("Appended to the %s located at %s", name, pwr.path));
        return pwr;
    }

    @Override
    public TimeCorrelationRollback.ProductRollbackOperation<ResolvedProductPath> getRollbackOperation(RollbackConfig config, Optional<String> newLatestProductVersion) throws MmtcException {
        if (! newLatestProductVersion.isPresent()) {
            // if we're rolling back to the initial state, then delete the entire file
            return new TimeCorrelationRollback.SingleFileDeletionOperation(resolveLocation(config));
        } else {
            // otherwise, truncate it
            return new TimeCorrelationRollback.TableTruncationOperation(resolveLocation(config), newLatestProductVersion);
        }
    }

    /**
     * Append new information to this output product on the filesystem (creating the file if it does not yet exist)
     *
     * @param ctx the current time correlation context, to read input and time correlation information from
     * @return the result of writing this product
     * @throws MmtcException if the product was not successfully written
     */
    public abstract ProductWriteResult appendToProduct(TimeCorrelationContext ctx) throws MmtcException;
}
