package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.products.definition.AppendedFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class OutputProductController extends BaseController {
    private final List<OutputProductDefinition<?>> outputProductDefs;

    public OutputProductController(MmtcWebAppConfig config) {
        super(config);
        this.outputProductDefs = config.getAllOutputProductDefs();
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        // list all products by name
        javalinApp.get("/api/v1/products/", ctx -> ctx.json(getAllOutputProductDefs()));

        // get the contents of a product
        javalinApp.get("/api/v1/products/{name}/{filename}", ctx -> {
            ctx.result(getOutputProductContents(ctx.pathParam("name"),  ctx.pathParam("filename")));
        });
    }

    private String getOutputProductContents(String outputProductName, String filename) throws MmtcException, IOException {
        OutputProductDefinition<?> productDef = config.getOutputProductDefByName(outputProductName);
        final Path filepathToRead;
        if (productDef instanceof EntireFileOutputProductDefinition) {
            EntireFileOutputProductDefinition entireFileDef =  (EntireFileOutputProductDefinition) productDef;
            filepathToRead = entireFileDef.resolveLocation(config).findAllMatching().stream()
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElseThrow(() -> new MmtcException("No file with filename: " + filename));
        } else if (productDef instanceof AppendedFileOutputProductDefinition) {
            AppendedFileOutputProductDefinition appendedFileDef =  (AppendedFileOutputProductDefinition) productDef;
            filepathToRead = appendedFileDef.resolveLocation(config).pathToProduct;
        } else {
            throw new MmtcException("Unexpected type for: " + outputProductName);
        }

        return String.join("\n", Files.readAllLines(filepathToRead));
    }

    public record OutputProductDef(String name, boolean builtIn, String simpleClassName, String type, boolean singleFile, List<String> filenames) { }

    private List<OutputProductDef> getAllOutputProductDefs() {
        return outputProductDefs.stream()
                .map(def -> {
                    try {
                        return new OutputProductDef(
                                def.getName(),
                                def.isBuiltIn(),
                                def.getClass().getSimpleName(),
                                getProductTypeAsString(def),
                                isSingleFileOutputProduct(def),
                                getExistingFilenamesForDef(def)
                            );
                    } catch (MmtcException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    private List<String> getExistingFilenamesForDef(OutputProductDefinition<?> def) throws MmtcException, IOException {
        if (def instanceof EntireFileOutputProductDefinition) {
            return ((EntireFileOutputProductDefinition) def)
                    .resolveLocation(config)
                    .findAllMatching()
                    .stream()
                    .map(p -> p.getFileName().toString())
                    .sorted(Collections.reverseOrder()) // to sort most recent filenames first
                    .toList();
        }

        if (def instanceof AppendedFileOutputProductDefinition) {
            AppendedFileOutputProductDefinition appendedDef = (AppendedFileOutputProductDefinition) def;
            Path pathToProduct = appendedDef.resolveLocation(config).pathToProduct;
            if (Files.exists(pathToProduct)) {
                return List.of(pathToProduct.getFileName().toString());
            } else {
                return Collections.emptyList();
            }
        }

        throw new MmtcException("Unexpected type for: " + def.getName());
    }

    private static String getProductTypeAsString(OutputProductDefinition<?> def) throws MmtcException {
        if (def instanceof EntireFileOutputProductDefinition) {
            return "EntireFileOutputProductDefinition";
        }

        if (def instanceof AppendedFileOutputProductDefinition) {
            return "AppendedFileOutputProductDefinition";
        }

        throw new MmtcException("Unexpected type for: " + def.getName());
    }

    private static boolean isSingleFileOutputProduct(OutputProductDefinition<?> def) throws MmtcException {
        if (def instanceof EntireFileOutputProductDefinition) {
            return false;
        }

        if (def instanceof AppendedFileOutputProductDefinition) {
            return true;
        }

        throw new MmtcException("Unexpected type for: " + def.getName());
    }
}
