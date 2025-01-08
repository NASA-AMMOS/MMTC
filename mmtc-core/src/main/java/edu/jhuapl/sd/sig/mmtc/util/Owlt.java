package edu.jhuapl.sd.sig.mmtc.util;

import spice.basic.*;
import java.util.Map;

/**
 * <P>The OWLT class is a static class that provides functions to compute the
 * one-way light travel time between a spacecraft and a ground station
 * on Earth.
 *
 * The primary functions in this class rely on the JPL/NAIF SPICE library
 * to perform their operations. The SPICE library is imported using the
 * JNISpice Java interface to their CSPICE library. Unless loaded already in
 * the calling application, the <code>initialize()</code> function must be
 * called to load CSPICE and the necessary SPICE kernels before any
 * other functions in this class are called. It need be called only once.
 *
 * <P>The NAIF SPICE documentation is extensive, available on-line, and provides
 * a full description of SPICE and its usage.
 *
 * @see <a href="https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/req/sclk.html">SCLK</a>
 * @see <a href="https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/index.html">CSPICE Toolkit</a>
 * @see <a href="https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/FORTRAN/req/naif_ids.html">NAIF Integer ID Codes</a>
 * @see <a href="https://public.ccsds.org/Pubs/301x0b4e1.pdf">CCSDS 301.0-B-4 Time Code Formats</a>
 *
 * <P>At minimum, the following SPICE kernels need to be loaded before the functions in
 * this class can be called. This list may not be complete as additional kernels may be
 * needd depending upon the mission.:
 *
 * <ul>
 *     <li> NAIF Generic Leap Seconds kernel (*.tls)
 *     <li> NAIF Generic earth stations fixed ephemeris kernel (earthstns_fx_*.bsp)
 *     <li> NAIF Generic earth stations fixed frame kernel (earth_fixed.fk)
 *     <li> NAIF Generic earth topographic frame kernel (earth_topo_*.tf)
 *     <li> NAIF Generic Planetary Constants kernel (pck0*.tpc)
 *     <li> Mission-Specific Spacecraft SCLK kernel (*.tsc)
 *     <li> Mission-specific Spacecraft Ephemeris kernel (*.bsp)
 * </ul>
 */
public class Owlt {

    /**
     * Indicates if the SPICE library and the kernels have been loaded.
     * @return true if the SPICE library has already been loaded
     */
    public static boolean isInitialized() {
        return TimeConvert.spiceLibLoaded() & TimeConvert.kernelsLoaded();
    }


    /**
     * Load the SPICE library and the indicated SPICE kernels.
     *
     * @param kernelsToLoad  IN a Map of strings that contain the names of SPICE kernels to load
     * @throws TimeConvertException when KernelDatabase.load() fails
     */
    public static void initialize(Map<String, String> kernelsToLoad) throws TimeConvertException {

        if (!TimeConvert.spiceLibLoaded()) {
            TimeConvert.loadSpiceLib();
        }

        if (!TimeConvert.kernelsLoaded()) {
            TimeConvert.loadSpiceKernels(kernelsToLoad);
        }
    }


    /**
     * Computes the downlink one-way light travel time (OWLT) between a spacecraft and an
     * Earth ground station in seconds. This value is computed for the given ground time
     * in Ephemeris Time (ET) seconds of the J2000 epoch. ET is equivalent to Barycentric
     * Dynamical Time (TDB).
     *
     * @param groundStation IN NAIF ground station name
     * @param naifScId      IN NAIF spacecraft ID
     * @param groundTimeEt  IN ground time for which OWLT is to be computed in ET
     * @return the OWLT in seconds
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double getDownlinkOwlt(int groundStation, int naifScId, Double groundTimeEt)
            throws TimeConvertException {

        double et = groundTimeEt;
        double[] ettarg = new double[1];
        double[] elapsed = new double[1];

        try {
            CSPICE.ltime(et, groundStation, "<-", naifScId, ettarg, elapsed);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error computing one-way light travel time for double time type: " + e.getMessage(), e);
        }

        return elapsed[0];
    }


    /**
     * Computes the downlink one-way light travel time (OWLT) between a spacecraft and an
     * Earth ground station in seconds. This value is computed for the given ground time
     * in UTC which is to be provided in ISO form yyyy-doyThh:mm:ss.ssssss or
     * form yyyy-mm-ddThh:mm:ss.ssssss.
     *
     * @param groundStation  IN NAIF ground station name
     * @param naifScId       IN NAIF spacecraft ID
     * @param groundTimeUtc  IN ground time for which OWLT is to be computed in UTC
     * @return the OWLT in seconds
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double getDownlinkOwlt(Integer groundStation, int naifScId, String groundTimeUtc)
            throws TimeConvertException {

        double et;

        try {
            et = CSPICE.str2et(groundTimeUtc);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error computing UTC to ET for one-way light travel time for string time type: " + e.getMessage(), e);
        }

        return getDownlinkOwlt(groundStation, naifScId, et);
    }
}
