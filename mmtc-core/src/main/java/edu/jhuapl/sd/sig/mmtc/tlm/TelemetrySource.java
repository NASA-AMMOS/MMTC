package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * An interface for the various types of telemetry sources that can be made available in
 * MMTC application, which can range from local files to database connections, etc.
 * <p>
 * This interface defines the functions that must be implemented to allow MMTC
 * to fulfill its range of operation.  This interface abstracts the source of input telemetry data
 * and is intended to eliminate the need for the rest of MMTC to know anything about the source of
 * its input telemetry.
 */
public interface TelemetrySource {

    /**
     * Returns the name of the TelemetrySource implementation.  If MMTC is started with the configuration key
     * 'telemetry.source.name' set to a value that matches the value returned by this method, this TelemetrySource
     * implementation will be used.
     *
     * @return the TelemetrySource's name
     */
    String getName();

    /**
     * If this Telemetry Source has required or optional CLI options it would like to expose, this
     * method should return a nonempty collection of the options.  This method is called once upon MMTC initialization.
     * <p>
     * If any of the returned options conflict with built-in MMTC CLI options, MMTC will log the issue and exit with a fatal error.
     *
     * @return additional options to add to the MMTC CLI
     */
    Collection<Option> getAdditionalCliArguments();

    /**
     * This method is called when MMTC configuration has been fully initialized and validated, and provides a chance for
     * TelemetrySource implementations to save a reference to the entire MMTC TimeCorrelationAppConfig instance or parts
     * therein.  It also provides an opportunity for TelemetrySource implementations to perform their own validation
     * before continuing.  A thrown MmtcException from this method will log the issue and prevent further processing.
     * <p>
     * For instance, an implementation should likely check whether the enabled set of MMTC filters is compatible with
     * the telemetry that the TelemetrySource implementation can provide.  If an incompatible filter is enabled, the
     * implementation should throw an MmtcException containing information about the issue.
     * <p>
     * This method is called once during an MMTC time correlation invocation, before any correlation processing occurs.
     *
     * @param config the complete TimeCorrelationAppConfig that MMTC will use to run, sourced from a TimeCorrelationConfigProperties.xml file
     * @throws MmtcException if there is an issue configuring the TelemetrySource, or another issue that indicates time correlation should not proceed
     */
    void applyConfiguration(TimeCorrelationAppConfig config) throws MmtcException;

    /**
     * If the Telemetry Source needs to make any long-lived 'connection' to the underlying source of telemetry,
     * it should do so in this method.  MMTC will call this method at least once during nominal MMTC time correlation
     * operations (before one or many calls to getSamplesInRange()), and possibly again before calling other
     * methods to retrieve ancillary telemetry.
     * <p>
     * Implementations of this class are expected to be able to support multiple connect and disconnect calls,
     * ordered as such.
     *
     * @throws MmtcException if there is a problem connecting to the underlying source of telemetry
     */
    void connect() throws MmtcException;

    /**
     * Disconnect from the underlying telemetry source, if necessary.
     * <p>
     * Implementations of this class are expected to be able to support multiple connect and disconnect calls,
     * ordered as such.
     *
     * @throws MmtcException if there is a problem cleanly disconnecting from the underlying source of telemetry
     */
    void disconnect() throws MmtcException;

    /**
     * Copy plugin-specific configuration to a sandbox directory and transform .  This is not called between connect/disconnect calls.
     *
     * @throws IOException if there is a problem copying the plugin's configuration to the sandbox directory
     * @return a map of config keys to values to apply to the new sandbox configuration
     */
    Map<String, String> sandboxTelemetrySourceConfiguration(MmtcConfig mmtcConfig, Path sandboxRoot, Path sandboxConfigRoot) throws IOException;

    /**
     * Query the underlying source of telemetry for all timekeeping telemetry within the given range of time (always given
     * as ERT, in UTC.)  Query should be inclusive of both bounds.  If no such telemetry is found within the range of
     * time, an empty list should be returned.
     *
     * @param startErt the start time of the query range
     * @param stopErt the stop time of the query range
     * @return a list of {@link FrameSample}s containing all telemetry received between the given ERT bounds, possibly empty
     * @throws MmtcException if the underlying query source cannot be successfully queried
     */
    List<FrameSample> getSamplesInRange(OffsetDateTime startErt, OffsetDateTime stopErt) throws MmtcException;

    /**
     * Returns a string that uniquely identifies the oscillator whose SCLK value provided the `tkSclk` values in the
     * given the FrameSample. If this cannot be done or is otherwise unknown, the string "-" should be returned.
     *
     * @param targetSample the target sample used for correlation
     * @return the radio ID, or the string "-" if unavailable
     */
    default String getActiveOscillatorId(FrameSample targetSample) { return "-"; }

    /**
     * For the oscillator identified by the given oscillator ID, retrieve its temperature in deg Celsius
     * ideally measured close in time to the given SCET (related to the chosen target sample used for time correlation.)
     *
     * @param scet the time nearby which the oscillator's temperature
     * @param oscillatorId the ID of the oscillator whose temperature should be returned
     * @return the oscillator's temperature in deg C, or Double.NaN if this telemetry does not exist or is otherwise expected to not be retrievable
     * @throws MmtcException if the value should be retrievable, but could not be retrieved
     */
    default double getOscillatorTemperature(OffsetDateTime scet, String oscillatorId) throws MmtcException { return Double.NaN; }

    /**
     * Returns a string that uniquely identifies the radio that radiated the given FrameSample.
     * If this cannot be done or is otherwise unknown, the string "-" should be returned.
     *
     * @param targetSample the target sample used for correlation
     * @return the radio ID, or the string "-" if unavailable
     */
    default String getActiveRadioId(FrameSample targetSample) { return "-"; }

    /**
     * Container class for TDT(S) and the related GNC parameters used to compute it onboard.
     */
    final class GncParms {
        // these three are typically parameters set onboard FSW and used as coefficients in the onboard GNC time conversions between SCLK and TDT
        // these should be captured by FSW, and retrieved from telemetry, in coherent/simultaneous sets (i.e. as set together on the spacecraft)
        private double sclk1;
        private double tdt1;
        private double clkchgrate1;

        // these are downlinked samples of the input (GNC SCLK) & output (TDT(S)) of running the above GNC time conversion (onboard the spacecraft)
        // these should be captured by FSW, and retrieved from telemetry, in coherent/simultaneous sets/pairs, such that any tdt_s value is the result of converting the gncsclk value
        private double gncsclk;
        private double tdt_s;

        public GncParms() {
            this.gncsclk     = Double.NaN;
            this.tdt_s       = Double.NaN;
            this.sclk1       = Double.NaN;
            this.tdt1        = Double.NaN;
            this.clkchgrate1 = Double.NaN;
        }

        public double getGncSclk()        { return gncsclk; }
        public double getTdt_s()       { return tdt_s;}
        public double getSclk1()       { return sclk1; }
        public double getTdt1()        { return tdt1; }
        public double getClkchgrate1() { return clkchgrate1; }

        public void setGncsclk(double gncsclk) {
            this.gncsclk = gncsclk;
        }
        public void setTdt_s(double tdt_s) {
            this.tdt_s = tdt_s;
        }
        public void setSclk1(double sclk1) {
            this.sclk1 = sclk1;
        }
        public void setTdt1(double tdt1) {
            this.tdt1 = tdt1;
        }
        public void setClkchgrate1(double clkchgrate1) {
            this.clkchgrate1 = clkchgrate1;
        }

        public String toString() {
            return this.gncsclk + ", " + this.tdt_s + ", " + sclk1 + ", " + tdt1 + ", " + this.clkchgrate1;
        }

        public boolean isEmpty() {
            return Stream.of(gncsclk, tdt_s, sclk1, tdt1, clkchgrate1).allMatch(d -> Double.isNaN(d));
        }
    }

    /**
     * Function to return TDT(S), and the GNC parameters and SCLK value used to compute it onboard.  Returned values
     * should have been recorded at a SCET deemed 'close enough' to (but not after) the time correlation's SCET,
     * by the TelemetrySource implementation and/or mission.
     *
     * @param noEarlierThanScet the earliest allowable SCET for the three FSW parameters
     * @param noEarlierThanTdtS the earliest allowable TDT for TDT(S) and SCLK for TDT(S) parameters
     * @return the GNC parameters closest to, but not after, the given SCET and TDT
     */
    default GncParms getGncTkParms(OffsetDateTime noEarlierThanScet, Double noEarlierThanTdtS) {
        return new GncParms();
    }
}
