package edu.jhuapl.sd.sig.mmtc.table;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents an instance of the Time History File, which consists mostly of
 * computed values, along with some raw telemetry.
 */
public class TimeHistoryFile extends AbstractTimeCorrelationTable {
    public static final String TARGET_FRAME_ENC_SCLK = "Enc_SCLK";
    public static final String TARGET_FRAME_SCLK_COARSE = "Int_SCLK";
    public static final String TDT_G = "TDT(G)";
    public static final String TDT_G_STR = "TDT(G)_Calendar";
    public static final String ET_G = "TDB(G)";  // NOTE: This is referred to as et (ephemeris time) elsewhere in the code.
    public static final String CLK_CHANGE_RATE = "ClkChgRate(s/s)";
    public static final String SCLK_DRIFT = "SCLK_Drift(ms/day)";
    public static final String OSCILLATOR = "Oscillator";
    public static final String OSCILLATOR_TEMP_DEGC = "Osc_Temp(degC)";
    public static final String CLK_CHANGE_RATE_MODE = "ClkChgRateMode";
    public static final String CLK_CHANGE_RATE_INTERVAL_DAYS = "ChrRateInterv(days)";
    public static final String EP = "Ep(ms)";
    public static final String EP_DT = "|Ep/dt|(ms/day)";
    public static final String DT = "Interval(days)";
    public static final String SCLK_PARTITION = "SCLK_Partition";
    public static final String RF_ENCODING = "RF_Encoding";
    public static final String STATION_ID = "GroundStationID";
    public static final String OWLT_SEC = "OWLT(sec)";
    public static final String SC_SUN_DIST_AU = "SpacecraftSunDist(AU)";
    public static final String SC_SUN_DIST_KM = "SpacecraftSunDist(km)";
    public static final String SC_EARTH_DIST_KM = "SpacecraftEarthDist(km)";
    public static final String EARTH_SUN_DIST_AU = "EarthSunDist(AU)";
    public static final String EARTH_SUN_DIST_KM = "EarthSunDist(km)";
    public static final String SC_VELOCITY_SSB = "SpacecraftVelocity(SSB_km/s)";
    public static final String EARTH_VELOCITY_SSB = "EarthVelocity(SSB_km/s)";
    public static final String SC_VELOCITY_EARTH = "SpacecraftVelocity(Earth_km/s)";
    public static final String RADIO_ID = "The Active Radio";
    public static final String DATA_RATE_BPS = "DataRate(bps)";
    public static final String SCET_UTC = "SCET (UTC)";
    public static final String TDT_S = "TDT(S)";
    public static final String TDT_S_STR = "TDT(S)_Calendar";
    public static final String TDT_S_ERROR = "TDT(S)_Error(ms)";
    public static final String SCLK_FOR_TDT_S = "SCLK_for_TDT(S)";
    public static final String TDT_S_ERROR_WARNING_THRESHOLD = "TDT(S)ErrorWarningThreshold(ms)";
    public static final String SCLK_1 = "SCLK1";
    public static final String TDT_1 = "TDT1";
    public static final String TDT_1_STRING = "TDT1_Calendar";
    public static final String CLK_CHANGE_RATE_FOR_TDT_S = "ClockChangeRateForTDT(S)";
    public static final String WARNING = "Warning";

    private static final List<String> DEFAULT_COLUMNS = Arrays.asList(
            TARGET_FRAME_ENC_SCLK,
            TARGET_FRAME_SCLK_COARSE,
            TDT_G,
            TDT_G_STR,
            ET_G,
            CLK_CHANGE_RATE,
            EP,
            EP_DT,
            DT,
            CLK_CHANGE_RATE_MODE,
            CLK_CHANGE_RATE_INTERVAL_DAYS,
            SCLK_DRIFT,
            OSCILLATOR,
            OSCILLATOR_TEMP_DEGC,
            SCLK_PARTITION,
            RF_ENCODING,
            STATION_ID,
            OWLT_SEC,
            SC_SUN_DIST_AU,
            SC_SUN_DIST_KM,
            SC_EARTH_DIST_KM,
            EARTH_SUN_DIST_AU,
            EARTH_SUN_DIST_KM,
            SC_VELOCITY_SSB,
            EARTH_VELOCITY_SSB,
            SC_VELOCITY_EARTH,
            RADIO_ID,
            DATA_RATE_BPS,
            SCET_UTC,
            TDT_S,
            TDT_S_STR,
            TDT_S_ERROR,
            SCLK_FOR_TDT_S,
            TDT_S_ERROR_WARNING_THRESHOLD,
            SCLK_1,
            TDT_1,
            TDT_1_STRING,
            CLK_CHANGE_RATE_FOR_TDT_S,
            RUN_TIME,
            WARNING
    );

    private final ArrayList<String> columns;

    public TimeHistoryFile(Path path) {
        this(path, Collections.emptyList());
    }

    public TimeHistoryFile(Path path, List<String> columnsToExclude) {
        super(path);
        this.columns = new ArrayList<>(DEFAULT_COLUMNS);
        this.columns.removeAll(columnsToExclude);
    }

    @Override
    public List<String> getHeaders() {
        return Collections.unmodifiableList(columns);
    }
}
