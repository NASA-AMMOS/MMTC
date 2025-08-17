package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public abstract class AppendedFileOutputProductDefinition extends OutputProductTypeDefinition<OutputProductTypeDefinition.ResolvedProductPath> {
    private static final Logger logger = LogManager.getLogger();

    public AppendedFileOutputProductDefinition(String name) {
        super(name);
    }

    @Override
    public final ProductWriteResult write(TimeCorrelationContext ctx) throws MmtcException {
        final ProductWriteResult pwr = writeToProduct(ctx);
        logger.info(USER_NOTICE, String.format("Appended to the %s located at %s", name, pwr.path));
        return pwr;
    }

    public abstract ProductWriteResult writeToProduct(TimeCorrelationContext ctx) throws MmtcException;

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
}
