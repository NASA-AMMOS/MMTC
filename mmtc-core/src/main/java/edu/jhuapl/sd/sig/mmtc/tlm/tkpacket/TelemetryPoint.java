package edu.jhuapl.sd.sig.mmtc.tlm.tkpacket;

import javax.xml.bind.annotation.XmlElement;

/**
 * Represents a TelemetryPoint element found in the timekeeping packet definition XML file.
 * This class is typically instantiated through XML binding; however, a constructor is
 * available for defining custom telemetry points without XML binding.
 */
public class TelemetryPoint {
    @XmlElement
    private String name;

    @XmlElement
    private int offset;

    @XmlElement
    private int length;

    @XmlElement
    private String type;

    public TelemetryPoint() {}

    /**
     * Initialization constructor.
     *
     * @param name The telemetry point name
     * @param offset The bit offset of the timekeeping packet field that corresponds to
     * this point
     * @param length The bit length of the timekeeping packet field that corresponds to
     * this point
     * @param type The primitive type that TimekeepingPacketIterator should interpret
     * the bits read from the timekeeping packet field. Expected values are UNSIGNED_INT,
     * SINGLE_FLOAT, and DOUBLE_FLOAT.
     */

    public TelemetryPoint(final String name, final int offset, final int length, final String type){
        this.name = name;
        this.offset = offset;
        this.length = length;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getType() {
        return type;
    }

}