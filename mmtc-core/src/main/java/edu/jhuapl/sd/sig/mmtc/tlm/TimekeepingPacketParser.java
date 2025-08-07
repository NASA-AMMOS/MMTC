package edu.jhuapl.sd.sig.mmtc.tlm;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import edu.jhuapl.sd.sig.mmtc.tlm.tkpacket.TelemetryPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import edu.jhuapl.sd.sig.mmtc.tlm.tkpacket.PacketDefinition;

/**
 * Parses files containing timekeeping packets. Provides iterators to iterate over the packets.
 *
 * NOTE: This class allows callers to supply timekeeping packet files at instantiation and/or
 * later on. If a new packet file is supplied, any iterators obtained previously will continue
 * to operate on the packet file that was active at the time they were obtained.
 */
public class TimekeepingPacketParser implements Iterable<TimekeepingRecord> {
    private static final Logger logger = LogManager.getLogger();

    private Unmarshaller unmarshaller;
    private PacketDefinition packetDefinition;
    private byte[] packets = null;

    /**
     * Constructor.
     *
     * @param packetDefinitionFile The XML file specifying the timekeeping packet fields. This
     * file must conform to src/main/resources/tk_packet.xsd.
     * @throws JAXBException if a JAXB exception occurred while parsing the TK packet description file
     * @throws SAXException if a SAX exception occurred while parsing the TK packet description file
     * @throws MalformedURLException if the TK packet description file could not be accessed
     */
    public TimekeepingPacketParser(Path packetDefinitionFile) throws JAXBException, SAXException, MalformedURLException, IllegalStateException {
        logger.trace("TimekeepingPacketParser: Instantiating XML parser");
        try {
            unmarshaller = JAXBContext.newInstance(PacketDefinition.class).createUnmarshaller();
            Schema packetSchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(getClass().getResource("/tk_packet.xsd"));
            unmarshaller.setSchema(packetSchema);
        } catch (JAXBException e) {
            logger.error("Error instantiating XML parser", e);
            throw e;
        } catch (SAXException e) {
            logger.error("Invalid XML schema file", e);
            throw e;
        }

        logger.trace("TimekeepingPacketParser: parsing XML packet definition [" + packetDefinitionFile + "]");

        try {
            packetDefinition = (PacketDefinition) unmarshaller.unmarshal(packetDefinitionFile.toFile());
            if (isValidPacketDef(packetDefinition)) {
                logger.trace("Packet definition validated successfully");
            }
        } catch (IllegalArgumentException e) {
            // NOTE: this indicates url is null. I don't think this is possible because URI.toURL() is a convenience method for {new URL(URI.toString())}, which can't produce null. but just in case...
            logger.error("Error locating XML packet definition file. Configuration value [" + packetDefinitionFile + "] produces a null URL.", e);
            throw e;
        } catch (JAXBException | IllegalStateException e) {
            logger.error("Error parsing XML packet definition file", e);
            throw e;
        }
    }

    /**
     * Class constructor.
     *
     * @param packetDefinitionFile The XML file specifying the timekeeping packet fields. This
     * file must conform to src/main/resources/tk_packet.xsd.
     * @param packetFile The binary file containing timekeeping packets
     * @throws IOException if the TK packet could not be read
     * @throws JAXBException if a JAXB exception occurred while parsing the TK packet description file
     * @throws SAXException if a SAX exception occurred while parsing the TK packet description file
     */
    public TimekeepingPacketParser(Path packetDefinitionFile, URI packetFile) throws IOException, JAXBException, SAXException {
        this(packetDefinitionFile);
        packets = Files.readAllBytes(new File(packetFile).toPath());
    }

    /**
     * Class constructor.
     *
     * @param packetDefinitionFile The XML file specifying the timekeeping packet fields. This
     * file must conform to src/main/resources/tk_packet.xsd.
     * @param packets The bytes of the packet(s) to be parsed
     * @throws JAXBException if a JAXB exception occurred while parsing the TK packet description file
     * @throws SAXException if a SAX exception occurred while parsing the TK packet description file
     * @throws MalformedURLException if the TK packet description file could not be accessed
     */
    public TimekeepingPacketParser(Path packetDefinitionFile, byte[] packets)
            throws MalformedURLException, JAXBException, SAXException {
        this(packetDefinitionFile);
        this.packets = packets;
    }

    /**
     * Provides an iterator over the time correlation packets found in the specified file.
     *
     * @param packetFile The binary file containing timekeeping packets
     * @return An iterator over the timekeeping packets found in the input file
     * @throws IOException if unable to iterate through the time correlation packets
     */
    public TimekeepingPacketIterator parsePackets(URI packetFile) throws IOException {
        packets = Files.readAllBytes(new File(packetFile).toPath());
        return (TimekeepingPacketIterator)iterator();
    }

    /**
     * Provides an iterator over the timekeeping packets found in the specified byte array.
     *
     * @param packets IN:The TK packets to be parsed
     * @return An iterator over the timekeeping packets found in the input array
     */
    public TimekeepingPacketIterator parsePackets(byte[] packets) {
        this.packets = packets;
        return (TimekeepingPacketIterator)iterator();
    }

    /**
     * Provides an iterator over the timekeeping packet data that was most recently supplied,
     * whether via the constructors or parsePackets().
     *
     * @return An iterator over the timekeeping packets found in the most recently supplied
     * data
     * @throws IllegalStateException If no timekeeping packet data has previously been supplied
     */
    @Override
    public Iterator<TimekeepingRecord> iterator() {
        if (packets == null) {
            throw new IllegalStateException("Packets data is uninitialized");
        }
        return new TimekeepingPacketIterator(packetDefinition, packets);
    }

    /**
     * Iterates over all TelemetryPoint fields in the packet definition file to
     * validate three primary aspects of the file: There are no duplicate fields,
     * all fields except DownlinkDataRate are always of type UNSIGNED_INT, and
     * no fields except DownlinkDataRate can have lengths greater than 32 bits and
     * only when its type is DOUBLE_FLOAT.
     * @param packetDef the active packet definition file that's being validated
     * @return true if all checks pass.
     * @throws IllegalStateException if any validation criteria fail.
     */
    public Boolean isValidPacketDef(PacketDefinition packetDef) throws IllegalStateException {
        Set<String> namesSet = new HashSet<>();
        for (TelemetryPoint telemetryPoint : packetDef.getTelemetryPoints()) {
            String name = telemetryPoint.getName();
            String type = telemetryPoint.getType();
            int length = telemetryPoint.getLength();

            // Validate that there are no duplicate telemetryPoint fields
            if (!namesSet.add(name)) {
                throw new IllegalStateException(String.format("Encountered duplicate field %s while parsing the packet definition file",name));
            }
            // Validate that all fields are specified as a valid unsigned int except for DownlinkDataRate
            try { // Ensure type is specified as one of three acceptable enumerated options
                TimekeepingPacketIterator.expectedType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(String.format("Invalid packet definition file: Type %s of field %s isn't an acceptable type.",type,name));
            }
            if (!type.equals("UNSIGNED_INT") && !name.equals("DownlinkDataRate")) {
                throw new IllegalStateException(String.format("Invalid type specified for field %s in the packet definition file. " +
                        "Only DownlinkDataRate can have type other than UNSIGNED_INT", name));
            }
            // Validate that all points have valid length for their given type
            if (name.equals("DownlinkDataRate")) {
                if ((type.equals("UNSIGNED_INT") || type.equals("SINGLE_FLOAT")) && length != 32) {
                    throw new IllegalStateException(String.format("The packet definition specifies DownlinkDataRate as a 32-bit data type " +
                            "but the specified length is %d bits.",length));
                } else if (type.equals("DOUBLE_FLOAT") && length != 64) {
                    throw new IllegalStateException(String.format("The packet definition specifies DownlinkDataRate as a 64-bit data type " +
                            "but the specified length is %d bits.",length));
                }
            } else { // For all fields other than Downlink
                if (length > 32) {
                    throw new IllegalStateException(String.format("Invalid length for field %s in packet definition file: " +
                            "Length cannot exceed 32 bits but is %d", name, length));
                }
            }
        }
        return true;
    }
    /**
     * Indicates whether the timekeeping packet definition includes a Downlink Data
     * Rate field.
     *
     * @return true if the definition contains Downlink Data Rate; false otherwise
     */
    public boolean packetsHaveDownlinkDataRate() {
        return packetDefinition.getTelemetryPoints().stream().anyMatch(p -> p.getName().equals("DownlinkDataRate"));
    }

    /**
     * Indicates whether the timekeeping packet definition includes an Invalid Flag
     * field.
     *
     * @return true if the definition contains Invalid Flag; false otherwise
     */
    public boolean packetsHaveInvalidFlag() {
        return packetDefinition.getTelemetryPoints().stream().anyMatch(p -> p.getName().equals("InvalidFlag"));
    }
}