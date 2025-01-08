package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import java.util.function.Function;

public class ChanValReadConfig {
    private static Function<Double, Boolean> DEFAULT_ACCEPTANCE_CRITERION = d -> true;

    public final String channelId;
    public final String readField;
    public final boolean retainAll;

    public Function<Double, Boolean> acceptanceCriterion = DEFAULT_ACCEPTANCE_CRITERION;

    public ChanValReadConfig(String channelId, String readField) {
        this.channelId = channelId;
        this.readField = readField;
        this.retainAll = false;
    }

    public ChanValReadConfig(String channelId, String readField, boolean retainAll) {
        this.channelId = channelId;
        this.readField = readField;
        this.retainAll = retainAll;
    }

    public void setAcceptanceCriterion(Function<Double, Boolean> acceptanceCriterion) {
        this.acceptanceCriterion = acceptanceCriterion;
    }
}
