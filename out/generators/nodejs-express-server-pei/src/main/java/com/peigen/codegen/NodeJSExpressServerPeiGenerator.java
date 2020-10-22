package com.peigen.codegen;

import org.openapitools.codegen.*;
import io.swagger.models.properties.*;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.NodeJSExpressServerCodegen;
import org.openapitools.codegen.meta.GeneratorMetadata;
import org.openapitools.codegen.meta.Stability;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.utils.URLPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import static org.openapitools.codegen.utils.StringUtils.camelize;

public class NodeJSExpressServerPeiGenerator extends DefaultCodegen implements CodegenConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(NodeJSExpressServerCodegen.class);
  public static final String EXPORTED_NAME = "exportedName";
  public static final String SERVER_PORT = "serverPort";

  protected String apiVersion = "1.0.0";
  protected String defaultServerPort = "8080";
  protected String implFolder = "services";
  protected String projectName = "openapi-server";
  protected String exportedName;

  public NodeJSExpressServerPeiGenerator() {
    super();

    modifyFeatureSet(features -> features
            .includeDocumentationFeatures(DocumentationFeature.Readme)
            .wireFormatFeatures(EnumSet.of(WireFormatFeature.JSON))
            .securityFeatures(EnumSet.of(
                    SecurityFeature.OAuth2_Implicit
            ))
            .excludeGlobalFeatures(
                    GlobalFeature.XMLStructureDefinitions,
                    GlobalFeature.Callbacks,
                    GlobalFeature.LinkObjects,
                    GlobalFeature.ParameterStyling
            )
            .excludeSchemaSupportFeatures(
                    SchemaSupportFeature.Polymorphism
            )
            .includeParameterFeatures(
                    ParameterFeature.Cookie
            )
    );

    generatorMetadata = GeneratorMetadata.newBuilder(generatorMetadata)
            .stability(Stability.BETA)
            .build();

    outputFolder = "generated-code/nodejs-express-server-pei";
    embeddedTemplateDir = templateDir = "nodejs-express-server-pei";

    setReservedWordsLowerCase(
            Arrays.asList(
                    "break", "case", "class", "catch", "const", "continue", "debugger",
                    "default", "delete", "do", "else", "export", "extends", "finally",
                    "for", "function", "if", "import", "in", "instanceof", "let", "new",
                    "return", "super", "switch", "this", "throw", "try", "typeof", "var",
                    "void", "while", "with", "yield")
    );

    additionalProperties.put("apiVersion", apiVersion);
    additionalProperties.put("implFolder", implFolder);

    // no model file
    modelTemplateFiles.clear();

    apiTemplateFiles.put("controller_module.mustache", ".mjs");
    apiTemplateFiles.put("service_module.mustache", ".mjs");
    apiTemplateFiles.put("handlers_module.mustache", ".js");

    supportingFiles.add(new SupportingFile("openapi_module.mustache", "src/api", "openapi.yaml"));
    supportingFiles.add(new SupportingFile("config_module.mustache", "src", "config.mjs"));
    supportingFiles.add(new SupportingFile("expressServer_module.mustache", "src", "expressServer.mjs"));
    supportingFiles.add(new SupportingFile("index_module.mustache", "src", "index.mjs"));
    supportingFiles.add(new SupportingFile("logger_module.mustache", "src", "logger.mjs"));
    supportingFiles.add(new SupportingFile("eslintrc.mustache", "", ".eslintrc.json"));

    // utils folder
    supportingFiles.add(new SupportingFile("utils" + File.separator + "openapiRouter.mustache", "src/utils", "openapiRouter.mjs"));

    // controllers folder
    supportingFiles.add(new SupportingFile("controllers" + File.separator + "index_module.mustache", "src/controllers", "index.mjs"));
    supportingFiles.add(new SupportingFile("controllers" + File.separator + "Controller_module.mustache", "src/controllers", "Controller.mjs"));
    // service folder
    supportingFiles.add(new SupportingFile("services" + File.separator + "index_module.mustache", "src/services", "index.mjs"));
    supportingFiles.add(new SupportingFile("services" + File.separator + "Service_module.mustache", "src/services", "Service.mjs"));

    // do not overwrite if the file is already present
    writeOptional(outputFolder, new SupportingFile("package.mustache", "", "package.json"));
    writeOptional(outputFolder, new SupportingFile("README.mustache", "", "README.md"));

    cliOptions.add(new CliOption(SERVER_PORT,
            "TCP port to listen on."));
  }

  @Override
  public String apiPackage() {
    return "src/controllers";
  }

  /**
   * Configures the type of generator.
   *
   * @return the CodegenType for this generator
   * @see org.openapitools.codegen.CodegenType
   */
  @Override
  public CodegenType getTag() {
    return CodegenType.SERVER;
  }

  /**
   * Configures a friendly name for the generator.  This will be used by the generator
   * to select the library with the -g flag.
   *
   * @return the friendly name for the generator
   */
  @Override
  public String getName() {
    return "nodejs-express-server-pei";
  }

  /**
   * Returns human-friendly help for the generator.  Provide the consumer with help
   * tips, parameters here
   *
   * @return A string value for the help message
   */
  @Override
  public String getHelp() {
    return "Generates a NodeJS Express server (alpha). IMPORTANT: this generator may subject to breaking changes without further notice).";
  }

  @Override
  public String toApiName(String name) {
    if (name.length() == 0) {
      return "DefaultApi";
    }
    return camelize(name);
  }

  @Override
  public String toApiFilename(String name) {
    return toApiName(name) + "Controller";
  }

  @Override
  public String apiFilename(String templateName, String tag) {
    String result = super.apiFilename(templateName, tag);

    LOGGER.info("apiFilename templateName is {} and tag is {} ", templateName, tag);
    LOGGER.info("apiFilename result: {}", result);

    if (templateName.equals("service.mustache") | templateName.equals("service_module.mustache")) {
      String stringToMatch = File.separator + "controllers" + File.separator;
      String replacement = File.separator + implFolder + File.separator;
      result = result.replace(stringToMatch, replacement);

      stringToMatch = "Controller.";
      replacement = "Service.";
      result = result.replace(stringToMatch, replacement);
      LOGGER.info("apiFilename for service result: {} ", result);
    } else if (templateName.equals("handlers.mustache") | templateName.equals("handlers_module.mustache")) {
      //String stringToMatch = File.separator + "controllers" + File.separator;
      //String replacement = File.separator + implFolder + File.separator;
      //result = result.replace(stringToMatch, replacement);

      String stringToMatch = "Controller.";
      String replacement = "Handlers.";
      result = result.replace(stringToMatch, replacement);
      LOGGER.info("apiFilename for handlers result: {} ", result);
    }
    return result;
  }

/*
    @Override
    protected String implFileFolder(String output) {
        return outputFolder + File.separator + output + File.separator + apiPackage().replace('.', File.separatorChar);
    }
*/

  /**
   * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
   * those terms here.  This logic is only called if a variable matches the reserved words
   *
   * @return the escaped term
   */
  @Override
  public String escapeReservedWord(String name) {
    if (this.reservedWordsMappings().containsKey(name)) {
      return this.reservedWordsMappings().get(name);
    }
    return "_" + name;
  }

  /**
   * Location to write api files.  You can use the apiPackage() as defined when the class is
   * instantiated
   */
  @Override
  public String apiFileFolder() {
    return outputFolder + File.separator + apiPackage().replace('.', File.separatorChar);
  }

  public String getExportedName() {
    return exportedName;
  }

  public void setExportedName(String name) {
    exportedName = name;
  }

  @Override
  public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
    @SuppressWarnings("unchecked")
    Map<String, Object> objectMap = (Map<String, Object>) objs.get("operations");
    @SuppressWarnings("unchecked")
    List<CodegenOperation> operations = (List<CodegenOperation>) objectMap.get("operation");
    for (CodegenOperation operation : operations) {
      operation.httpMethod = operation.httpMethod.toLowerCase(Locale.ROOT);

      List<CodegenParameter> params = operation.allParams;
      if (params != null && params.size() == 0) {
        operation.allParams = null;
      }
      List<CodegenResponse> responses = operation.responses;
      if (responses != null) {
        for (CodegenResponse resp : responses) {
          if ("0".equals(resp.code)) {
            resp.code = "default";
          }
        }
      }
      if (operation.examples != null && !operation.examples.isEmpty()) {
        // Leave application/json* items only
        for (Iterator<Map<String, String>> it = operation.examples.iterator(); it.hasNext(); ) {
          final Map<String, String> example = it.next();
          final String contentType = example.get("contentType");
          if (contentType == null || !contentType.startsWith("application/json")) {
            it.remove();
          }
        }
      }
    }
    return objs;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getOperations(Map<String, Object> objs) {
    List<Map<String, Object>> result = new ArrayList<>();
    Map<String, Object> apiInfo = (Map<String, Object>) objs.get("apiInfo");
    List<Map<String, Object>> apis = (List<Map<String, Object>>) apiInfo.get("apis");
    for (Map<String, Object> api : apis) {
      result.add((Map<String, Object>) api.get("operations"));
    }
    return result;
  }

  private static List<Map<String, Object>> sortOperationsByPath(List<CodegenOperation> ops) {
    Multimap<String, CodegenOperation> opsByPath = ArrayListMultimap.create();

    for (CodegenOperation op : ops) {
      opsByPath.put(op.path, op);
    }

    List<Map<String, Object>> opsByPathList = new ArrayList<>();
    for (Entry<String, Collection<CodegenOperation>> entry : opsByPath.asMap().entrySet()) {
      Map<String, Object> opsByPathEntry = new HashMap<>();
      opsByPathList.add(opsByPathEntry);
      opsByPathEntry.put("path", entry.getKey());
      opsByPathEntry.put("operation", entry.getValue());
      List<CodegenOperation> operationsForThisPath = Lists.newArrayList(entry.getValue());
      operationsForThisPath.get(operationsForThisPath.size() - 1).hasMore = false;
      if (opsByPathList.size() < opsByPath.asMap().size()) {
        opsByPathEntry.put("hasMore", "true");
      }
    }

    return opsByPathList;
  }

  @Override
  public void processOpts() {
    super.processOpts();

    if (StringUtils.isEmpty(System.getenv("JS_POST_PROCESS_FILE"))) {
      LOGGER.info("Environment variable JS_POST_PROCESS_FILE not defined so the JS code may not be properly formatted. To define it, try 'export JS_POST_PROCESS_FILE=\"/usr/local/bin/js-beautify -r -f\"' (Linux/Mac)");
      LOGGER.info("NOTE: To enable file post-processing, 'enablePostProcessFile' must be set to `true` (--enable-post-process-file for CLI).");
    }

    if (additionalProperties.containsKey(EXPORTED_NAME)) {
      setExportedName((String) additionalProperties.get(EXPORTED_NAME));
    }

    /*
     * Supporting Files.  You can write single files for the generator with the
     * entire object tree available.  If the input file has a suffix of `.mustache
     * it will be processed by the template engine.  Otherwise, it will be copied
     */
    // supportingFiles.add(new SupportingFile("controller.mustache",
    //   "controllers",
    //   "controller.js")
    // );
  }

  @Override
  public void preprocessOpenAPI(OpenAPI openAPI) {
    URL url = URLPathUtils.getServerURL(openAPI, serverVariableOverrides());
    String host = URLPathUtils.getProtocolAndHost(url);
    String port = URLPathUtils.getPort(url, defaultServerPort);
    String basePath = url.getPath();

    if (additionalProperties.containsKey(SERVER_PORT)) {
      port = additionalProperties.get(SERVER_PORT).toString();
    }
    this.additionalProperties.put(SERVER_PORT, port);

    if (openAPI.getInfo() != null) {
      Info info = openAPI.getInfo();
      if (info.getTitle() != null) {
        // when info.title is defined, use it for projectName
        // used in package.json
        projectName = info.getTitle()
                .replaceAll("[^a-zA-Z0-9]", "-")
                .replaceAll("^[-]*", "")
                .replaceAll("[-]*$", "")
                .replaceAll("[-]{2,}", "-")
                .toLowerCase(Locale.ROOT);
        this.additionalProperties.put("projectName", projectName);
      }
    }

    // need vendor extensions
    Paths paths = openAPI.getPaths();
    if (paths != null) {
      for (String pathname : paths.keySet()) {
        PathItem path = paths.get(pathname);
        Map<HttpMethod, Operation> operationMap = path.readOperationsMap();
        if (operationMap != null) {
          for (HttpMethod method : operationMap.keySet()) {
            Operation operation = operationMap.get(method);
            String tag = "default";
            if (operation.getTags() != null && operation.getTags().size() > 0) {
              tag = toApiName(operation.getTags().get(0));
            }
            if (operation.getOperationId() == null) {
              operation.setOperationId(getOrGenerateOperationId(operation, pathname, method.toString()));
            }
            // add x-openapi-router-controller
//                        if (operation.getExtensions() == null ||
//                                operation.getExtensions().get("x-openapi-router-controller") == null) {
//                            operation.addExtension("x-openapi-router-controller", sanitizeTag(tag) + "Controller");
//                        }
//                        // add x-openapi-router-service
//                        if (operation.getExtensions() == null ||
//                                operation.getExtensions().get("x-openapi-router-service") == null) {
//                            operation.addExtension("x-openapi-router-service", sanitizeTag(tag) + "Service");
//                        }
            if (operation.getExtensions() == null ||
                    operation.getExtensions().get("x-eov-operation-handler") == null) {
              operation.addExtension("x-eov-operation-handler", "controllers/" + sanitizeTag(tag) + "Controller");
            }
          }
        }
      }
    }

  }

  @Override
  public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
    generateYAMLSpecFile(objs);

    for (Map<String, Object> operations : getOperations(objs)) {
      @SuppressWarnings("unchecked")
      List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");

      List<Map<String, Object>> opsByPathList = sortOperationsByPath(ops);
      operations.put("operationsByPath", opsByPathList);
    }
    return super.postProcessSupportingFileData(objs);
  }

  @Override
  public String removeNonNameElementToCamelCase(String name) {
    return removeNonNameElementToCamelCase(name, "[-:;#]");
  }

  @Override
  public String escapeUnsafeCharacters(String input) {
    return input.replace("*/", "*_/").replace("/*", "/_*");
  }

  @Override
  public String escapeQuotationMark(String input) {
    // remove " to avoid code injection
    return input.replace("\"", "");
  }

  @Override
  public void postProcessFile(File file, String fileType) {
    if (file == null) {
      return;
    }

    String jsPostProcessFile = System.getenv("JS_POST_PROCESS_FILE");
    if (StringUtils.isEmpty(jsPostProcessFile)) {
      return; // skip if JS_POST_PROCESS_FILE env variable is not defined
    }

    // only process files with js extension
    if ("js".equals(FilenameUtils.getExtension(file.toString()))) {
      String command = jsPostProcessFile + " " + file.toString();
      try {
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        int exitValue = p.exitValue();
        if (exitValue != 0) {
          LOGGER.error("Error running the command ({}). Exit code: {}", command, exitValue);
        }
        LOGGER.info("Successfully executed: " + command);
      } catch (Exception e) {
        LOGGER.error("Error running the command ({}). Exception: {}", command, e.getMessage());
      }
    }
  }
}