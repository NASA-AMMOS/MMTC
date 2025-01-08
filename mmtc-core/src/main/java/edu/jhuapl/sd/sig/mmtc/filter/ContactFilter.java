package edu.jhuapl.sd.sig.mmtc.filter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;


/**
 * Class that implements the Contact Filter which compares clock drift rates between the current
 * and the previous contact. If the rates differ by more than a configurable threshold, the sample
 * set is rejected.
 *
 *  {@code driftRate = ((sclk_c - sclk_p) / (TDT(G)_c - TDT(G)_p) - 1) * ms/day }
 *
 *  where sclk_c and tdt_c are the values from the current target sample and sclk_p and tdt_p are the
 *  values from the previous contact time correlation being compared against.
 *  To pass the Contact Filter the driftRate must fall within the upper and lower delta driftrate
 *  thresholds read from configuration parameters:
 *  {@code (driftRate >= dDRIFTRATE_lower) and (driftRate <= dDRIFTRATE_upper) }
 */
public class ContactFilter {
    private static final Logger logger = LogManager.getLogger();

    // The encoded SCLK and TDT(G) from the previous time correlation (i.e., from the previous contact).
    private String encsclk_p;
    private String tdt_g0_p_str;

    // The raw SCLK and TDT(G) from the current time correlation.
    private double tdtG_c;

    /**
     * Sets the SCLK from the previous time correlation.
     * @param encsclk The SCLK from the previous time correlation
     */
    public void setEncSclk_previous(String encsclk) {
        this.encsclk_p = encsclk;
    }

    /**
     * Sets the TDT(G) from the previous time correlation.
     * @param tdt_g0str The TDT(G) from the current time correlation
     */
    public void setTdt_g_previous(String tdt_g0str) {
        this.tdt_g0_p_str = tdt_g0str;
    }

    /**
     * Sets the TDT(G) from the current time correlation.
     * @param tdt_g0 The TDT(G) from the current time correlation
     */
    public void setTdt_g_current(double tdt_g0) {
        this.tdtG_c = tdt_g0;
    }

    /**
     * This is the main function of the Contact Filter.
     * Verifies the consistency in SCLK drift rates from one contact to the next.
     * This function computes the drift between successive contacts. If the
     * computed drift is within the upper and lower bound thresholds (inclusive)
     * given in the configuration parameters, the function returns true. Otherwise,
     * it returns false. It applies the formula:
     *       <code>driftRate = (deltaSclk/deltaTdtG - 1) * milliseconds_per_day</code>
     * where <i>deltaSclk</i> is the difference between the two successive raw SCLK
     * values in the contacts and deltaTdtG is the difference between the two
     * successive <i>TDT(G)</i> values in the contacts.
     *
     * @param targetSample the target sample
     * @param config the app config containing the threshold value
     * @param sclk_kernel_fine_tick_modulus the fine tick modulus used in the SCLK kernel
     * @return true if the delta drift rate is equal to or less than the
     *  configurable threshold
     * @throws MmtcException when any conversions fail
     */
    public boolean process(FrameSample targetSample, TimeCorrelationAppConfig config, Integer sclk_kernel_fine_tick_modulus) throws MmtcException {
        try {
            boolean passesFilter = true;

            // Get the upper and lower delta driftrate (dDRIFTRATE) values from configuration parameters.
            BigDecimal dDriftrate_UpperThreshold = new BigDecimal(config.getContactFilterDeltaUpperThreshold());
            BigDecimal dDriftrate_LowerThreshold = new BigDecimal(config.getContactFilterDeltaLowerThreshold());

            logger.debug("ContactFilter.process(): Previous Contact encoded SCLK = " + encsclk_p +
                    ", Previous Contact TDT(G) Str = " + tdt_g0_p_str + ".");

            // The TDT(G) read from the SCLK Kernel contains a leading '@' character that SPICE cannot parse. If this
            // character is there, remove it.
            String tdt_g0_previous;
            if (!Character.isDigit(tdt_g0_p_str.charAt(0))) {
                tdt_g0_previous = tdt_g0_p_str.substring(1, tdt_g0_p_str.length() - 1);
            } else {
                tdt_g0_previous = tdt_g0_p_str;
            }

            Double priorEncSclk = Double.parseDouble(encsclk_p);
            int naifScId = config.getNaifSpacecraftId();

            // The SCLK and TDT(G) from the previous time correlation (i.e., from last contact).
            int sclk_p = TimeConvert.encSclkToSclk(naifScId, sclk_kernel_fine_tick_modulus, priorEncSclk).intValue();
            Double tdtG_p = TimeConvert.tdtStrToTdt(tdt_g0_previous);

            logger.debug("ContactFilter.process(): Previous contact SCLK = " + sclk_p +
                    ", Previous TDT(G) = " + tdtG_p + "." + " Current SCLK = " + targetSample.getTkSclkCoarse() +
                    ", current TDT(G) = " + tdtG_c + ".");


            // Compute the drift rate using BigDecimal arithmetic so as not to lose precision:
            //       driftRate = (deltaSclk/deltaTdtG - 1) * milliseconds_per_day
            BigDecimal deltaSclk =
                    new BigDecimal(targetSample.getTkSclkCoarse() - sclk_p);
            BigDecimal deltaTdtG = new BigDecimal(tdtG_c - tdtG_p);
            BigDecimal rate = deltaSclk.divide(deltaTdtG, 12, RoundingMode.HALF_UP);       // deltaSclk/deltaTdtG
            BigDecimal adjrate = rate.subtract(BigDecimal.ONE);                                  // subtract 1

            // Multiply the drift by ms/day and round to the number of significant figures.
            BigDecimal driftRate = adjrate.multiply(new BigDecimal(86400000.000000000000)).setScale(3, RoundingMode.HALF_UP);

            DecimalFormat df = new DecimalFormat("#0.000");
            logger.debug("ContactFilter.process(): driftRate = " + df.format(driftRate) + ".");

            // driftRate is <= configured threshold
            if (driftRate.compareTo(dDriftrate_LowerThreshold) <= 0) {
                logger.warn("Contact Filter FAILED: Target Sample at ERT " + targetSample.getErtStr() +
                        " failed Contact Filter due to drift rate of " + df.format(driftRate) +
                        " falling below lower threshold of " + df.format(dDriftrate_LowerThreshold) + ".");
                passesFilter = false;
            }
            // driftRate is >= configured threshold
            else if (driftRate.compareTo(dDriftrate_UpperThreshold) >= 0) {
                logger.warn("Contact Filter FAILED: Target Sample at ERT " + targetSample.getErtStr() +
                        " failed Contact Filter due to drift rate of " + df.format(driftRate) +
                        " falling above upper threshold of " + df.format(dDriftrate_UpperThreshold) + ".");
                passesFilter = false;
            }

            return passesFilter;
        } catch (TimeConvertException e) {
            throw new MmtcException("Could not run Contact Filter", e);
        }
    }
}
