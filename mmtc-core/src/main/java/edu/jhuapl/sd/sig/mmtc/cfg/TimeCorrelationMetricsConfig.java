package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.time.OffsetDateTime;

public interface TimeCorrelationMetricsConfig {
    double getFrameErtBitOffsetError();
    Integer getTkSclkFineTickModulus() throws TimeConvertException;
    int getNaifSpacecraftId();

    boolean isTestMode();
    double getTestModeOwlt();

    String getStationId(int pathId) throws MmtcException;

    int getSclkPartition(OffsetDateTime groundReceiptTime);

    double getSpacecraftTimeDelaySec();
}
