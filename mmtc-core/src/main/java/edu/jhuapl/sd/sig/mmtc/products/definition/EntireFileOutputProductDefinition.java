package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public abstract class EntireFileOutputProductDefinition extends OutputProductDefinition<OutputProductDefinition.ResolvedProductDirAndPrefix> {
    private static final Logger logger = LogManager.getLogger();

    public EntireFileOutputProductDefinition(final String name) {
        super(name);
    }

    @Override
    public final ProductWriteResult write(TimeCorrelationContext ctx) throws MmtcException {
        final ProductWriteResult pwr = writeNewProduct(ctx);
        logger.info(USER_NOTICE, String.format("Wrote new %s to %s", name, pwr.path));
        return pwr;
    }

    public abstract ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException;

    @Override
    public final TimeCorrelationRollback.MultiFileDeletionOperation getRollbackOperation(RollbackConfig config, Optional<String> newLatestProductVersion) throws MmtcException {
        return new TimeCorrelationRollback.MultiFileDeletionOperation(
                resolveLocation(config),
                newLatestProductVersion
        );
    }
}
