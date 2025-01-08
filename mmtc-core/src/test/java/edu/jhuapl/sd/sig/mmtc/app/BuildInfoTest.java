package edu.jhuapl.sd.sig.mmtc.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BuildInfoTest {
    @Test
    public void simpleTest() {
        BuildInfo buildInfo = new BuildInfo();

        assertNotEquals(BuildInfo.UNKNOWN_VALUE, buildInfo.version);
        assertTrue(buildInfo.version.length() > 0);

        assertNotEquals(BuildInfo.UNKNOWN_VALUE, buildInfo.buildDate);
        assertTrue(buildInfo.buildDate.length() > 0);

        assertNotEquals(BuildInfo.UNKNOWN_VALUE, buildInfo.commit);
        assertTrue(buildInfo.commit.length() > 0);
    }
}