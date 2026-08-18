package edu.jhuapl.sd.sig.mmtc.reporting;

import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;

public abstract class Templater<T> {
    protected final T ctx;
    private final String builtInDefaultTemplateFilename;

    public Templater(T ctx, String builtInDefaultTemplateFilename) {
        this.ctx = ctx;
        this.builtInDefaultTemplateFilename = builtInDefaultTemplateFilename;
    }

    protected Template getTemplate(Optional<Path> templatePath) throws IOException {
        if (templatePath.isPresent()) {
            return getTemplateAtPath(templatePath.get());
        } else {
            return getBuiltInTemplate();
        }
    }

    protected Configuration getBaseFreemarkerConfig() {
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_34);
        freemarkerConfig.setClassForTemplateLoading(HtmlTemplater.class, "/templating/templates");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(
                TemplateExceptionHandler.RETHROW_HANDLER
        );

        BeansWrapperBuilder beansWrapperBuilder = new BeansWrapperBuilder(Configuration.VERSION_2_3_34);
        beansWrapperBuilder.setExposeFields(true);
        freemarkerConfig.setObjectWrapper(beansWrapperBuilder.build());

        return freemarkerConfig;
    }

    protected Template getBuiltInTemplate() throws IOException {
        Configuration freemarkerConfig = getBaseFreemarkerConfig();
        return freemarkerConfig.getTemplate(builtInDefaultTemplateFilename);
    }

    protected Template getTemplateAtPath(Path templatePath) throws IOException {
        Configuration freemarkerConfig = getBaseFreemarkerConfig();
        freemarkerConfig.setDirectoryForTemplateLoading(templatePath.getParent().toFile());
        return freemarkerConfig.getTemplate(templatePath.getFileName().toString());
    }

    // helper method
    protected String renderTemplateString(Object model, String templateString) throws IOException {
        Configuration freemarkerConfig = getBaseFreemarkerConfig();

        Template template = new Template(
                "dynamic-template-string",
                new StringReader(templateString),
                freemarkerConfig
        );

        try (StringWriter writer = new StringWriter()) {
            template.process(model, writer);
            return writer.toString();
        } catch (TemplateException e){
            throw new IOException(e);
        }
    }
}
