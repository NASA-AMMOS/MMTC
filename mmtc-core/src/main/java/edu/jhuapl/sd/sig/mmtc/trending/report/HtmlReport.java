package edu.jhuapl.sd.sig.mmtc.trending.report;

import edu.jhuapl.sd.sig.mmtc.trending.TrendingContext;
import edu.jhuapl.sd.sig.mmtc.trending.config.TrendingConfig;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;

public class HtmlReport {

    private final TrendingConfig config;
    private final TrendingContext ctx;

    public HtmlReport(TrendingConfig config, TrendingContext ctx) {
        this.config = config;
        this.ctx = ctx;
    }

    public void write() throws IOException {
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_34);
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(
                TemplateExceptionHandler.RETHROW_HANDLER
        );
        freemarkerConfig.setClassForTemplateLoading(HtmlReport.class, "/trending_templates");

        Template template = freemarkerConfig.getTemplate("trending_report.html");

        try (Writer writer = Files.newBufferedWriter(config.getTrendingHtmlReportOutputPath())) {
            template.process(ctx, writer);
        } catch (TemplateException e){
            throw new IOException(e);
        }

    }
}
