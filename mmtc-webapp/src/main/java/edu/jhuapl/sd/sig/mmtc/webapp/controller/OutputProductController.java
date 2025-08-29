package edu.jhuapl.sd.sig.mmtc.webapp.controller;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.products.definition.AppendedFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.util.GenericCsv;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.service.OutputProductService;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OutputProductController extends BaseController {
    private final OutputProductService outputputProductService;

    public OutputProductController(MmtcWebAppConfig config, OutputProductService outputProductService) {
        super(config);
        this.outputputProductService = outputProductService;
    }

    @Override
    public void registerEndpoints(Javalin javalinApp) {
        // list all products by name
        javalinApp.get("/api/v1/products/", ctx -> ctx.json(outputputProductService.getAllOutputProductDefs()));

        // get the contents of a product
        javalinApp.get("/api/v1/products/{name}/{filename}", ctx -> {
            ctx.result(getOutputProductContents(ctx.pathParam("name"),  ctx.pathParam("filename")));
        });

        // get the contents of a product as table rows
        javalinApp.get("/api/v1/productsAsTable/{name}/{filename}", ctx -> {
            ctx.json(getOutputProductContentsAsTable(ctx.pathParam("name"),  ctx.pathParam("filename")));
        });
    }

    record TableResult (
            List<String> columns,
            List<Map<String, String>> rows
    ) { }

    private TableResult getOutputProductContentsAsTable(String outputProductName, String filename) throws MmtcException, IOException {
        Path filePathForOutputProduct = getFilePathForOutputProduct(outputProductName, filename);
        GenericCsv csv = new GenericCsv(filePathForOutputProduct);
        return new TableResult(
                csv.getHeaders(),
                csv.getRows()
        );
    }

    private String getOutputProductContents(String outputProductName, String filename) throws MmtcException, IOException {
        Path filePathForOutputProduct = getFilePathForOutputProduct(outputProductName, filename);
        return String.join("\n", Files.readAllLines(filePathForOutputProduct));
    }

    private Path getFilePathForOutputProduct(String outputProductName, String filename) throws MmtcException, IOException {
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

        return filepathToRead;
    }
}
