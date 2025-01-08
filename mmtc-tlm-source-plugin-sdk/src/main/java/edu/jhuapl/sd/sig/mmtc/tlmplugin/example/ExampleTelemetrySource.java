package edu.jhuapl.sd.sig.mmtc.tlmplugin.example;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.commons.cli.Option;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ExampleTelemetrySource implements TelemetrySource {
    // some constants to help in generating fake telemetry
    // a typical implementation of a telemetry source will not need to know such values, as they should be read directly from the underyling source
    final int MAX_VCFC = 2048;

    private TimeCorrelationAppConfig config;

    @Override
    public String getName() {
        /*
         * This is the first call that MMTC makes to any given TelemetrySource during a correlation run.
         * It is used to determine the unique name for a TelemetrySource implementation, and is used to select the configured TelemetrySource among all available implementaitons for the run.
         */
        return "ExampleTelemetrySource";
    }

    @Override
    public Collection<Option> getAdditionalCliArguments() {
        /*
         * This is the second call that MMTC makes to the configured TelemetrySource during a correlation run.
         * It gives the TelemetrySource a chance to add additional CLI options/arguments to correlation runs.
         * The presence/absence of all arguments (MMTC's base arguments, plus any defined here), as well as the values defined on any arguments that take values, will be present in the TimeCorrelationAppConfig object passed to applyConfiguration below.
         * MMTC will fail with a fatal error at runtime if an argument is defined that would duplicate the short or long version of an argument that the base MMTC implementation defines.
         *
         * Here, we define an example argument.
         */

        return Arrays.asList(
                new Option(
                        "b",
                        "number-of-frames-to-generate",
                        true,
                        "Specifies the number of fake FrameSamples to generate"
                )
        );
    }

    @Override
    public void applyConfiguration(TimeCorrelationAppConfig config) throws MmtcException {
        /*
         * This is the third call that method is called:
         * - after the complete MMTC configuration for the run is read into a TimeCorrelationAppConfig object and validated.
         * - before any other call (besides getAdditionalCliArguments)
         *
         * Typically, you'll want to create a reference to the TimeCorrelationAppConfig object for future use in other methods.
         * This is also where plugins should check for any enabled filters that does not make sense to run with the telemetry that they can produce.
         * See the MMTC User Guide, and/or the source of the filters in edu.jhuapl.sd.sig.mmtc.filter, for more information.
         */
        this.config = config;

        /*
         * Let's imagine that our example mission's telemetry does not have a Master Channel Frame Counter.
         * This TelemetrySource, thus, will not populate that field, and we should not allow the corresponding filter to be enabled:
         */
        Set<String> enabledFilters = config.getFilters().keySet();
        if (enabledFilters.contains(TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER)) {
            String errorString = "When using ExampleTelemetrySource, the " +
                    TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER +
                    " filter is not applicable and must be disabled by setting the configuration option " +
                    "filter.<filter name>.enabled to false.";
            throw new MmtcException(errorString);
        }
    }

    @Override
    public void connect() throws MmtcException {
        /*
         * If your underlying telemetry archive/C&T system needs to establish a connection (e.g. database, web w/auth, etc.), here's where you'd have an opportunity to connect to it.
         * This is called after applyConfiguration and before any `get` methods.  It may be called multiple times, with each call followed by a `disconnect`.
         * This example implementation is a no-op.
         */
    }

    @Override
    public void disconnect() {
        /*
         * This is called after a call to `connect()` and one or many `get` methods. It may be called multiple times, one time for each call to `connect()`.
         * This example implementation is a no-op.
         */
    }

    @Override
    public List<FrameSample> getSamplesInRange(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {
        /*
         * This is the primary method that:
         * - retrieves timekeeping telemetry from the underlying source of telemetry with an Earth/Ground Receipt Time (ERT/GRT) within the given start and stop times (inclusive)
         * - manipulates/parses it as necessary and populates FrameSamples to return to the core MMTC program
         *
         * Not all fields on FrameSample are required for core MMTC time correlation functionality.  See documentation for details.
         *
         * If there is no telemetry available within the queried time range, an empty list must be returned.
         *
         * It is critical that these FrameSamples are populated accurately and precisely.  Any deficiency here could result in inaccurate or incorrect time correlation products being produced.  It is vital that the plugin developer and mission test the end-to-end implementation of MMTC.
         *
         * For this example plugin, we'll generate some FrameSamples that mimic sequential frames downlinked from a spacecraft.
         * A realistic plugin would interface with the underlying telemetry source (making web calls to a webservice, using RPCs, running subprocesses, etc.)
         */

        // for the purposes of this example, assume a 100kbps downlink data rate
        final int downlinkDataRateBps = 100_000;

        /*
         * FrameSamples must include at least one of:
         * - their donwlink data rate
         * - their frame size (which will be used in combination with the duration between subsequent ERTs) to estimate their downlink data rate)
         *
         * For this example, we'll set each sample's frame size in bits, but not their downlink data rate, to demonstrate MMTC's downlink data rate estimation logic
         */
        final int frameSizeBits = 16384;
        final int framePeriodMs = (int) (((frameSizeBits * 1.0) / downlinkDataRateBps) * 1000);

        OffsetDateTime currentErt = start;
        OffsetDateTime nextErt = start.plus(framePeriodMs, ChronoUnit.MILLIS);
        int currentVcfc = 0;

        List<FrameSample> results = new ArrayList<>();

        // example usage of a command-line argument
        final int maxNumFramesToGenerate;

        if (config.cmdLineHasOption('b')) {
            maxNumFramesToGenerate = Integer.parseInt(config.getCmdLineOptionValue('b'));
        } else {
            maxNumFramesToGenerate = 2000;
        }

        while (! currentErt.isAfter(stop) && results.size() < maxNumFramesToGenerate) {
            FrameSample frameSample = new FrameSample();

            // -------- these values are usually sourced from the 'current' frame --------

            frameSample.setFrameSizeBits(frameSizeBits);

            // of course, in reality the ERT and the SCLK will come from independent sources, but for this example we'll use them together to generate sane fake telemetry
            final SclkPair currentSclkPair = generateFakeSclkPair(currentErt);
            frameSample.setSclkCoarse(currentSclkPair.coarseTicks);
            frameSample.setSclkFine(currentSclkPair.fineTicks);

            try {
                frameSample.setErt(TimeConvert.isoUtcToCds(TimeConvert.timeToIsoUtcString(currentErt)));
            } catch (TimeConvertException e) {
                throw new MmtcException("Could not convert ERT to CDS", e);
            }

            // pretend that these frames are all downlinked via DSS-14
            frameSample.setPathId(14);

            frameSample.setVcid(0);
            frameSample.setVcfc(currentVcfc % MAX_VCFC);

            // -------- these values are usually sourced from the 'next' frame --------

            // final SclkPair nextSclkPair = generateFakeSclkPair(nextErt);
            // NOTE: the actual 'tk' values should come from a following packet, but for the purposes of this example, we'll reuse the same ones
            frameSample.setTkSclkCoarse(currentSclkPair.coarseTicks);
            frameSample.setTkSclkFine(currentSclkPair.fineTicks);
            frameSample.setTkVcid(0);
            frameSample.setTkVcfc(currentVcfc % MAX_VCFC);

            // -------- these values are usually sourced from the 'next' frame --------
            frameSample.setSuppVcid(0);
            frameSample.setSuppVcfc((currentVcfc + 1) % MAX_VCFC);

            try {
                frameSample.setSuppErt(TimeConvert.isoUtcToCds(TimeConvert.timeToIsoUtcString(nextErt)));
            } catch (TimeConvertException e) {
                throw new MmtcException("Could not convert ERT to CDS", e);
            }

            results.add(frameSample);

            currentVcfc += 1;
            currentErt = nextErt;
            nextErt = currentErt.plus(framePeriodMs, ChronoUnit.MILLIS);
        }

        return results;
    }

    // internal utility class
    private static class SclkPair {
        public final int coarseTicks;
        public final int fineTicks;

        public SclkPair(int coarseTicks, int fineTicks) {
            this.coarseTicks = coarseTicks;
            this.fineTicks = fineTicks;
        }
    }

    private SclkPair generateFakeSclkPair(OffsetDateTime ert) {
        final OffsetDateTime epoch = OffsetDateTime.of(2006, 01, 19, 18, 8, 0, 0, ZoneOffset.UTC);
        final int SCLK_FINE_MOD = 65_536;

        // this of course does not handle leap seconds, but core MMTC does
        Duration durationSinceEpoch = Duration.between(
                epoch,
                ert
        );

        // pretend that the spacecraft clock was set slightly late
        int coarseTicks = (int) durationSinceEpoch.getSeconds();
        coarseTicks -= 2;

        // not necessarily realistic, but pretend that there's some small variance in the fine tick counter
        long fineTicks = durationSinceEpoch.minusSeconds(coarseTicks).getNano() / 1_000_000;
        if (fineTicks > 5 && fineTicks < SCLK_FINE_MOD - 5) {
            Random ran = new Random();
            fineTicks += ran.nextInt(10) - 5;
        }

        return new SclkPair(coarseTicks, (int) fineTicks);
    }
}