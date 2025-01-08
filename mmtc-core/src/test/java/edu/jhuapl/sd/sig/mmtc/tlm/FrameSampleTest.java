package edu.jhuapl.sd.sig.mmtc.tlm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FrameSampleTest {

    @Test
    public void testFrameSizeSet() {
        FrameSample fs = new FrameSample();
        assertFalse(fs.isFrameSizeBitsSet());
        fs.setFrameSizeBits(1234);
        assertTrue(fs.isFrameSizeBitsSet());
        assertEquals(1234, fs.getFrameSizeBits());
    }
}