package edu.jhuapl.sd.sig.mmtc.tlm.tkpacket;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents the Packet_Definition root element in the timekeeping packet definition
 * XML file. This class is intended to be instantiated through XML binding.
 */
@XmlRootElement(name = "Packet_Definition")
public class PacketDefinition {
    @XmlElement(name = "TelemetryPoint", type = TelemetryPoint.class)
    private List<TelemetryPoint> telemetryPoints;

    /**
     * Returns the list of TelemetryPoint elements found in the timekeeping packet
     * definition XML file.
     * @return The telemetry points of this packet definition
     */
    public List<TelemetryPoint> getTelemetryPoints() {
        return telemetryPoints;
    }
}
