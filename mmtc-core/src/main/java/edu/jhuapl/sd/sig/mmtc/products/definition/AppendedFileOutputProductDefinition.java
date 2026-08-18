package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.rollback.config.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

/**
 * Defines an output product that is represented by a single text file, which has one or many lines appended to it with each successful time correlation.
 */
public abstract class AppendedFileOutputProductDefinition extends OutputProductDefinition<ResolvedProductPath> {
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
    public final TimeCorrelationRollback.ProductRollbackOperation<ResolvedProductPath> getRollbackOperation(RollbackConfig config, Optional<String> newLatestProductVersion) throws MmtcException {
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

    protected static String getFormattedDryRunOutputForTableRow(List<String> headers, TableRecord rec) {
        List<String> thValues = rec.getValues();
        String zippedThRow = IntStream.range(0, headers.size())
                .mapToObj(i -> "\t" + headers.get(i) + "\t:\t"+thValues.get(i))
                .collect(Collectors.joining("\n"));

        return zippedThRow;
    }
}
