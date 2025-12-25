package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals.ChanValReadConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class AmpcsTelemetrySourceConfig {
    private final MmtcConfigWithTlmSource timeCorrelationAppConfig;
    private final Map<Integer, String> vcidsToOscillatorIds = new HashMap<>();

    public AmpcsTelemetrySourceConfig(MmtcConfigWithTlmSource timeCorrelationAppConfig) {
        this.timeCorrelationAppConfig = timeCorrelationAppConfig;

        if (getActiveOscillatorSelectionMode().equals(ActiveOscillatorSelectionMode.by_vcid)) {
            for (String oscillatorId : timeCorrelationAppConfig.getValidOscillatorIds()) {
                final String vcidsKey = String.format("telemetry.source.plugin.ampcs.oscillators.%s.vcids", oscillatorId);

                if (!timeCorrelationAppConfig.containsNonEmptyKey(vcidsKey)) {
                    throw new IllegalStateException(String.format("When using by-vcid oscillator identification, the key '%s' must be specified.", vcidsKey));
                }

                for (String vcidForOscillator : timeCorrelationAppConfig.getStringArray(vcidsKey)) {
                    vcidsToOscillatorIds.put(
                            Integer.parseInt(vcidForOscillator),
                            oscillatorId
                    );
                }
            }
        }
    }

    String getConnectionParms() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.additionalChillCliParms");
    };

    /**
     * Gets the frame header fine SCLK modulus.
     *
     * @return the frame header fine SCLK modulus
     */
    public int getPacketHeaderFineSclkModulus() {
        return timeCorrelationAppConfig.getInt("telemetry.source.plugin.ampcs.tkPacket.tkPacketHeaderFineSclkModulus");
    }

    /**
     * Gets the timeout in seconds for AMPCS chill_* subprocess calls. When running a chill_*
     * query, MMTC will terminate the process if it hasn't completed within this time.
     * 
     * @return the number of seconds to wait before timing out
     */
    public int getChillTimeoutSec() {
        return timeCorrelationAppConfig.getInt("telemetry.source.plugin.ampcs.chillTimeoutSec");
    }

    /**
     * Gets the Application Identifier (APID) for the time correlation packet (TK packet). This is the APID
     * that will be used to query the telemetry archive for TK packets. This is applicable only
     * when time correlation data is obtained from a specially purposed packet (i.e., not from frame headers or
     * RawTlmTable).
     *
     * @return the APID of the TK packet
     */
    public String getTKPacketApid() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.apid");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * APID returned.
     *
     * @return the field name of the APID in chill_get metadata
     */
    public String getTkPacketApidFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.apidFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Spacecraft Event Time (SCET).
     *
     * @return the field name of the SCET in chill_get metadata
     */
    public String getTkPacketScetFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.scetFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Earth Receive Time (ERT) associated with a packet returned by a query.
     *
     * @return the field name of the ERT in chill_get metadata
     */
    public String getTkPacketErtFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.ertFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Spacecraft Clock Time (SCLK). Note that this is usually NOT the SCLK used for time correlation
     * computations. Normally, that will come from the body of the TK packet.
     *
     * @return the field name of the SCLK in chill_get metadata
     */
    public String getTkPacketSclkFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.sclkFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Virtual Channel ID (VCID) associated with a packet returned by a query.
     *
     * @return the field name of the VCID in chill_get metadata
     */
    public String getTkPacketVcidFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.vcidFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Virtual Channel Frame Count (VCFC) associated with a packet returned by a query.
     *
     * @return the field name of the VCFC in chill_get metadata
     */
    public String getTkPacketVcfcFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.vcfcFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Ground Station identifier (DSS) associated with a packet returned by a query.
     *
     * @return the field name of the ground station identifier in chill_get metadata
     */
    public String getTkPacketDssIdFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.dssIdFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * length of a packet (e.g., the TK packet) returned from a query.
     *
     * @return the field name of the packet length in chill_get metadata
     */
    public String getTkPacketLengthFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.pktLengthFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * identifier of the channel (Channel ID) that was returned from a query.
     *
     * @return the field name of the channel ID in chill_get metadata
     */
    public String getChannelIdFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.channel.channelIdFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Spacecraft Event Time (SCET) associated with a channel returned by a query.
     *
     * @return the field name of the channel SCET in chill_get metadata
     */
    public String getChannelScetFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.channel.channelScetFieldName");
    }

    /**
     * The known size of frames in bits for a particular spacecraft to be used in downlink rate computation
     * if frameSizeBits is absent from telemetry for any reason
     * @return the default length of downlink frames in bits
     */
    public int getFrameSizeBits() {
        return Integer.parseInt(timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.frameSizeBits"));
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Earth Receive Time (ERT) associated with a frame returned by a query.
     *
     * @return the field name of the ERT in chill_get metadata
     */
    public String getFrameErtFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.frame.ertFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Ground Station identifier (DSS) associated with a frame returned by a query.
     *
     * @return the field name of the ground station identifier in chill_get metadata
     */
    public String getFrameDssIdFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.frame.dssIdFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Virtual Channel ID (VCID) associated with a frame returned by a query.
     *
     * @return the field name of the VCID in chill_get metadata
     */
    public String getFrameVcidFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.frame.vcidFieldName");
    }

    /**
     * The name of the field in the CSV metadata output from a chill_get* command that contains the
     * Virtual Channel Frame Count (VCFC) associated with a frame returned by a query.
     *
     * @return the field name of the VCFC in chill_get metadata
     */
    public String getFrameVcfcFieldName() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.frame.vcfcFieldName");
    }

    /**
     * Only applicable when using the AMPCS_CHILL_WITH_FRAMES telemetry source. When searching for
     * the target frame corresponding to a TK packet, search for frames whose ERT is at most this
     * many seconds earlier than the TK packet ERT.
     *
     * @return the maximum time range, in seconds, in which to search for a TK packet's target frame
     */
    public int getMaxTkpacketFrameSeparation() {
        return timeCorrelationAppConfig.getInt("telemetry.source.plugin.ampcs.frame.maxTkpacketFrameSeparation");
    }

    /**
     * Gets the size of a TK packet in bytes (including packet headers) from configuration.
     * @return the size of the TK packet in bytes
     */
    public int tkPacketSize() {
        return timeCorrelationAppConfig.getInt("telemetry.source.plugin.ampcs.tkpacket.tkPacketSizeBytes");
    }

    /**
     * Gets the name of the XML file that describes the format of the TK packet.
     *
     * @return the path to the TK description packet
     */
    public Path getTkPacketDescriptionFilePath() {
        return timeCorrelationAppConfig.ensureAbsolute(
                Paths.get(timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.tkpacket.tkPacketDescriptionFile.path"))
        );
    }

    public enum ActiveOscillatorSelectionMode {
        none,
        fixed,
        by_vcid
    }

    public ActiveOscillatorSelectionMode getActiveOscillatorSelectionMode() {
        if (! timeCorrelationAppConfig.containsNonEmptyKey("telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode")) {
            throw new IllegalStateException("Please set a value for config key telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode");
        }

        return ActiveOscillatorSelectionMode.valueOf(timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode"));
    }

    public String getFixedActiveOscillatorId() {
        return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.oscillator.fixedActiveOscillatorId");
    }

    /**
     * Gets configuration-provided channel value information for one of the following:
     * - sclk1, the telemetry point (channel) as defined in the FSW dictionary which contains the SCLK time from onboard the spacecraft that is used to compute time onboard
     * - tdt1, the telemetry point (channel) as defined in the FSW dictionary which contains the TDT time from onboard the spacecraft that is used to compute time onboard
     * - tdts, the telemetry point (channel) as defined in the FSW dictionary which contains the TDT time from onboard the spacecraft that was computed onboard for a given SCLK measurement
     * - gncsclk, the telemetry point (channel) as defined in the FSW dictionary which contains the GNC SCLK time from onboard the spacecraft that was used to compute TDT(S)
     * - tdtChgRate, the telemetry point (channel) as defined in the FSW dictionary which contains the clock change rate from onboard the spacecraft that was computed onboard for a given SCLK measurement
     *
     * @param channel one of the channel types as given above
     * @param retainAll whether to retain all values for the channel regardless of target SCET
     *
     * @return a ChannelValReadConfig describing how to read the channel value from the archive, if the configuration is present
     */
    public Optional<ChanValReadConfig> getGncChanValReadConfig(String channel, boolean retainAll) {
        if (! Arrays.asList("sclk1", "tdt1", "tdtChgRate", "gncsclk", "tdts").contains(channel)) {
            throw new IllegalArgumentException("No such queryable channel in MMTC: " + channel);
        }

        return getChanValReadConfig("telemetry.source.plugin.ampcs.channel." + channel, retainAll);
    }

    public String getActiveOscillatorIdByVcid(final int vcid) {
        if (vcidsToOscillatorIds.containsKey(vcid)) {
            return vcidsToOscillatorIds.get(vcid);
        } else {
            throw new IllegalStateException(String.format("No known oscillator ID for VCID: %d; please check MMTC configuration.", vcid));
        }
    }

    public Optional<ChanValReadConfig> getOscTempChanValReadConfig(String oscillatorId) {
        if (! timeCorrelationAppConfig.getValidOscillatorIds().contains(oscillatorId)) {
            throw new IllegalArgumentException("No such oscillator ID: " + oscillatorId);
        }

        return getChanValReadConfig(String.format("telemetry.source.plugin.ampcs.oscillators.%s.temperature", oscillatorId), false);
    }

    private Optional<ChanValReadConfig> getChanValReadConfig(String baseKey, boolean retainAll) {
        final String configKey = baseKey + ".channelId";
        final String readField = baseKey + ".readField";

        if (!timeCorrelationAppConfig.containsAllNonEmptyKeys(configKey, readField)) {
            return Optional.empty();
        }

        return Optional.of(new ChanValReadConfig(
                timeCorrelationAppConfig.getString(configKey),
                timeCorrelationAppConfig.getString(readField),
                retainAll
        ));
    }

    /**
     * Gets the ID of the currently active radio that is providing SCLK values for time
     * correlation. This applies to only to missions in which the radio provides the
     * SCLK vales for time correlation. If an active radio value is not provided, this
     * is not an error. Return '-'.
     *
     * @return the character identifying the currently active radio.
     */
    public String getActiveRadioId() {
        if (timeCorrelationAppConfig.containsNonEmptyKey("telemetry.source.plugin.ampcs.activeRadioId")) {
            return timeCorrelationAppConfig.getString("telemetry.source.plugin.ampcs.activeRadioId");
        } else {
            return "-";
        }
    }

    public boolean isAmpcsTlmWithFramesBatchingEnabled() {
        if (timeCorrelationAppConfig.containsNonEmptyKey("telemetry.source.plugin.ampcs.chill_get_frames.batching")) {
            return timeCorrelationAppConfig.getBoolean("telemetry.source.plugin.ampcs.chill_get_frames.batching");
        } else {
            return true;
        }
    }
}
