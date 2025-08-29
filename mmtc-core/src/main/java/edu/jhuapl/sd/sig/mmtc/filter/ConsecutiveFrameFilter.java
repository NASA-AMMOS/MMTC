package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class ConsecutiveFrameFilter implements TimeCorrelationFilter {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Performs five checks:
     *
     * <ul>
     *
     * <li>Checks that all samples in the sample set have the same VCID. If not, the
     * sample set is rejected.</li>
     *
     * <li>Checks that all samples in the sample set have consecutive VCFCs. If not,
     * a warning is logged, but the sample set is not rejected. The exception to
     * this is when the mission-defined maximum VCFC value is reached and the
     * counter rolls over back to zero, in which case the rollover is merely logged
     * as info.</li>
     * 
     * <li>Checks that each sample's VCID is the same as its supplemental sample's
     * VCID iff the supplemental sample's VCID is available. If not, the sample set
     * is rejected. Some missions and/or telemetry sources don't use/provide the
     * VCID of the supplemental sample, so if it isn't available, this check is not
     * performed but a warning is logged.</li>
     *
     * <li>Checks that each sample's VCFC value is supplementalSampleOffset less
     * than its supplemental sample's VCFC iff the supplemental sample's VCFC is
     * available. In other words, verifies that timekeeping packets and their target
     * samples are separated by the number of samples configured for the mission. If
     * not, the sample set is rejected. Some missions and/or telemetry sources don't
     * use/provide the VCFC of the sample and/or the VCFC of the supplemental
     * sample, so if either isn't available, this check is not performed but a
     * warning is logged.</li>
     *
     * <li>Checks that each sample's VCID and VCFC matches its supplemental sample's
     * TK VCID and TK VCFC. In other words, verifies that the target sample VCID and
     * VCFC values reported by a timekeeping packet match the target sample's actual
     * VCID and VCFC. Some telemetry sources don't provide the VCFC of the sample,
     * so if it isn't available, then that portion of the check is not performed but
     * a warning is logged.</li>
     *
     * </ul>
     *
     * @param samples the sample set
     * @param config  the app configuration
     * @return true if the VCID is consistent among all samples, false otherwise
     */
    @Override
    public boolean process(List<FrameSample> samples, TimeCorrelationRunConfig config) throws MmtcException {
        if (samples.isEmpty()) {
            logger.warn("Consecutive Frame Filter Failed: Attempted to filter an empty sample set.");
            return false;
        }

        final int supplementalSampleOffset = config.getSupplementalSampleOffset();
        final int vcfcMaxValue = config.getVcfcMaxValue();
        if (! (vcfcMaxValue > 0)) {
            throw new MmtcException("When using the ConsecutiveFrameFilter, the VCFC maximum value "+
            "telemetry.vcfcMaxValue must be set in configuration to a positive integer.");
        }

        int previousVcid = -1;
        int currentVcid;
        int currentTkVcid;
        int previousVcfc = -1;
        int currentVcfc;
        int currentTkVcfc;
        int supplementalSampleVcid;
        int supplementalSampleVcfc;

        for (FrameSample sample : samples) {
            currentVcid = sample.getVcid();
            currentTkVcid = sample.getTkVcid();
            currentVcfc = sample.getVcfc();
            currentTkVcfc = sample.getTkVcfc();
            supplementalSampleVcid = sample.getSuppVcid();
            supplementalSampleVcfc = sample.getSuppVcfc();

            logger.debug("Consecutive Frames Filter evaluating sample with ERT " + sample.getErtStr() +
                    " (" + sample.getErt() + ") and VCID " + String.valueOf(currentVcid) + ".");

            // Check 1: All samples must have same VCID. Reject otherwise.
            if (previousVcid == -1) {
                previousVcid = currentVcid;
            }
            else if (currentVcid != previousVcid) {
                logger.warn("Consecutive Frame Filter Failed (Check 1): Sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") was received on VCID " + String.valueOf(currentVcid) +
                        ", but previous sample in set was received on VCID " + String.valueOf(previousVcid) +
                        ". The samples in the set might not be consecutive.");
                return false;
            }
            previousVcid = currentVcid;

            // Check 2: Samples must have consecutive VCFCs, excluding rollbacks. Log otherwise.
            if (previousVcfc == -1) {
                previousVcfc = currentVcfc;
            }
            else if (currentVcfc == 0 && previousVcfc == vcfcMaxValue) {
                logger.info("Consecutive Frame Filter info (Check 2): Mission max VCFC value "+
                        String.valueOf(vcfcMaxValue)+" has been reached, rolling over.");
            }
            else if (currentVcfc != previousVcfc + 1) {
                logger.warn("Consecutive Frame Filter warning (Check 2): Sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") had VCFC " + String.valueOf(currentVcfc) +
                        ", but previous sample in set had VCFC " + String.valueOf(previousVcfc) +
                        ". The samples in the set might not be consecutive.");
            }
            previousVcfc = currentVcfc;

            // Check 3: If supplemental sample VCID is available, sample VCID and supplemental sample VCID must match.
            // Reject otherwise. If supplemental sample VCID isn't available, log a warning.
            if (supplementalSampleVcid == -1) {
                logger.warn("Consecutive Frame Filter (Check 3): cannot verify that sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") and its supplemental sample were received on " +
                        "the same VCID. The supplemental sample VCID is not available.");
            }
            else if (currentVcid != supplementalSampleVcid) {
                logger.warn("Consecutive Frame Filter Failed (Check 3): Sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") and its supplemental sample were not received on " +
                        "the same VCID. The sample was received on VCID " + String.valueOf(currentVcid) +
                        ", but its supplemental sample was received on VCID " +
                        String.valueOf(supplementalSampleVcid) + ".");
                return false;
            }

            // Check 4: If sample VCFC and supplemental sample VCFC are available, sample VCFC must be
            //supplementalSampleOffset less than supplemental sample VCFC. Reject otherwise. If sample VCFC or
            //supplemental sample VCFC isn't available, log a warning.
            if (currentVcfc == -1 || supplementalSampleVcfc == -1) {
                String msg = "supplemental sample VCFC is";
                if (currentVcfc == -1 && supplementalSampleVcfc == -1) {
                    msg = "sample VCFC and supplemental sample VCFC are";
                }
                else if (currentVcfc == -1) {
                    msg = "sample VCFC is";
                }
                logger.warn("Consecutive Frame Filter (Check 4): cannot verify that sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") and its supplemental sample are separated by " +
                        "the configured supplemental sample offset. The " + msg + " not available.");
            }
            else if (supplementalSampleVcfc == ((currentVcfc + supplementalSampleOffset) % (vcfcMaxValue + 1))) {
                // Do nothing
            }
            else {
                logger.warn("Consecutive Frame Filter Failed (Check 4): Sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") and its supplemental sample are not separated by " +
                        "the configured supplemental sample offset of " + String.valueOf(supplementalSampleOffset) +
                        ". The sample had VCFC " + String.valueOf(currentVcfc) + " and its supplemental " +
                        "sample had VCFC " + String.valueOf(supplementalSampleVcfc) + ".");
                return false;
            }

            // Check 5a: Sample VCID and supplemental sample TK VCID must match. Reject otherwise.
            if (currentVcid != currentTkVcid) {
                logger.warn("Consecutive Frame Filter Failed (Check 5a): Sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") had VCID " + String.valueOf(currentVcid) +
                        ", but its supplemental sample's target sample VCID was " + String.valueOf(currentTkVcid) + ".");
                return false;
            }
            // Check 5b: If sample VCFC is available, sample VCFC and supplemental sample TK VCFC must match. Reject
            // otherwise. If sample VCFC isn't available, log a warning.
            if (currentVcfc == -1) {
                logger.warn("Consecutive Frame Filter cannot verify that sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") has a VCFC matching its supplemental sample's target sample " +
                        "VCFC. The sample VCFC is not available.");
            }
            else if (currentVcfc != currentTkVcfc) {
                logger.warn("Consecutive Frame Filter Failed (Check 5b): Sample with ERT " + sample.getErtStr() +
                        " (" + sample.getErt() + ") had VCFC " + String.valueOf(currentVcfc) +
                        ", but its supplemental sample's target sample VCFC was " + String.valueOf(currentTkVcfc)
                        + ".");
                return false;
            }
        }
        return true;
    }

}
