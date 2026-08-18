package edu.jhuapl.sd.sig.mmtc.reporting;

import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;

public abstract class HtmlTemplater<T> extends Templater<T> {
    public HtmlTemplater(T ctx, String builtInDefaultTemplateFilename) {
        super(ctx, builtInDefaultTemplateFilename);
    }

    @Override
    protected Configuration getBaseFreemarkerConfig() {
        Configuration freemarkerConfig = super.getBaseFreemarkerConfig();
        freemarkerConfig.setOutputFormat(HTMLOutputFormat.INSTANCE);
        return freemarkerConfig;
    }
}
