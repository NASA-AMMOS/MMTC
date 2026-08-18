package edu.jhuapl.sd.sig.mmtc.cfg.app;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

public interface TimekeepingAdjustmentParameters {
    // double getFrameErtBitOffsetError();
    // Integer getTkSclkFineTickModulus() throws TimeConvertException;
    // int getNaifSpacecraftId();

    boolean isTestMode();
    double getTestModeOwlt();

    // String getStationId(int pathId) throws MmtcException;

    // int getSclkPartition(OffsetDateTime groundReceiptTime);

    // double getSpacecraftTimeDelaySec();
}
