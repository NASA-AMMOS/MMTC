package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class FrameSampleValidator {

    /**
     * Validates that all given FrameSample have the minimum set of fields populated required to perform time correlation:
     * - ert
     * - pathId
     * - tkSclkCoarse
     * - tkSclkFine
     * - tkDataRateBps
     * - derivedTdBe
     *
     * Also validates that the group of frame samples has unique ERTs.
     *
     * @param samples FrameSamples to validate
     * @param tk_sclk_fine_tick_modulus the TK fine tick modulus
     * @throws MmtcException if any frame sample in the given collection are not sufficiently populated for time correlation usage
     */
    public static void validate(final List<FrameSample> samples, final int tk_sclk_fine_tick_modulus) throws MmtcException {
        // check that each sample passes individual validation
        for (FrameSample fs : samples) {
            List<String> validationFailureMessages = new ArrayList<>();

            if (fs.getErt().isNull()) {
                validationFailureMessages.add("ERT must be set");
            }

            if (fs.getPathId() == -1) {
                validationFailureMessages.add("pathId must be set");
            }

            if (fs.getTkSclkCoarse() == -1) {
                validationFailureMessages.add("tkSclkCoarse must be set");
            }

            if (fs.getTkSclkFine() == -1) {
                validationFailureMessages.add("tkSclkFine must be set");
            }

            if (fs.getTkSclkFine() >= tk_sclk_fine_tick_modulus) {
                validationFailureMessages.add(String.format("tkSclkFine value (%d) is equal to or larger than tk_sclk_fine_tick_modulus (%d)", fs.getTkSclkFine(), tk_sclk_fine_tick_modulus));
            }

            if (fs.getTkDataRateBps().doubleValue() <= 0) {
                validationFailureMessages.add("tkDataRateBps must be set (must be greater than 0)");
            }

            if (Double.isNaN(fs.getDerivedTdBe())) {
                validationFailureMessages.add("derivedTdBe must be set (must be not NaN)");
            }

            if (validationFailureMessages.size() > 0) {
                throw new MmtcException(
                        "FrameSample failed validation: \n"
                                + fs + "\n"
                                + "Validation failures\n"
                                + "-------------------\n"
                                + validationFailureMessages.stream().map(m -> String.format("- %s", m)).collect(Collectors.joining("\n"))
                                + "\nPlease check the telemetry source configuration and/or implementation and ensure that the returned FrameSamples contain all relevant telemetry."
                );
            }
        }

        // check that the group of samples have unique ERTs among themselves
        final List<CdsTimeCode> uniqueErts = samples.stream().map(FrameSample::getErt).distinct().collect(Collectors.toList());
        if (uniqueErts.size() != samples.size()) {
            throw new MmtcException(
                    String.format(
                            "FrameSamples failed validation: repeated ERTs were found among the sample set.  Sample ERTs: %s",
                            uniqueErts.stream().map(cdsErt -> {
                                try {
                                    return TimeConvert.cdsToIsoUtc(cdsErt);
                                } catch (TimeConvertException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .collect(Collectors.toList())
                    )
            );
        }
    }
}