package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Executable contract lock for {@code /operators} (the M1 close). Two halves:
 *
 * <ol>
 *   <li><b>Runtime wire behaviour</b> — real requests prove the boundary is camelCase, the tombstone
 *       is a derived {@code deleted} boolean (never an internal status enum), the list response is a
 *       top-level object (never a bare array), and 400/404 problems are
 *       {@code application/problem+json} carrying {@code traceId}.
 *   <li><b>Generated document lock</b> — the live {@code /v3/api-docs} is parsed and asserted to
 *       carry the whole contract surface, including the POST/PUT 400 {@code anyOf} (NOT
 *       {@code oneOf}) with both {@code ValidationProblem} and {@code Problem} registered; it is
 *       byte-equivalent to the committed {@code docs/openapi/operators.yml} (staleness gate) and
 *       surface-equivalent to the in-repo {@code docs/openapi/api-contract.locked.yaml} (the lock —
 *       the IT reads an in-repo path because {@code workareas/} is not present in a CI checkout).
 * </ol>
 *
 * <p>Regenerate the committed artifact with {@code mvn verify -Dopenapi.generate=true} after an
 * intentional API change; a plain build then fails until it is committed.
 */
class OperatorComplianceIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORGANISATION_ID = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";

  private static final Path GENERATED_DOC = Path.of("docs/openapi/operators.yml");
  private static final Path LOCKED_CONTRACT = Path.of("docs/openapi/api-contract.locked.yaml");

  private static final String CREATE_BODY =
      """
      {
        "name": "Highland Livestock Ltd",
        "addressLine1": "14 Drover's Way",
        "townOrCity": "Inverness",
        "postcode": "IV2 3JH",
        "countryCode": "GB",
        "phone": "+44 1463 234567",
        "email": "exports@highlandlivestock.example.com"
      }
      """;

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  // ---- runtime wire behaviour -------------------------------------------------------------

  @Test
  void createAndReadEmitCamelCasePropertiesAndADerivedDeletedBoolean() throws Exception {
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.addressLine1").value("14 Drover's Way"))
        .andExpect(jsonPath("$.townOrCity").value("Inverness"))
        .andExpect(jsonPath("$.countryCode").value("GB"))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.modifiedAt").exists())
        // the tombstone is a derived boolean, never the internal status enum (cv-016)
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.status").doesNotExist())
        // never the snake_case forms, and no retired typed fields
        .andExpect(jsonPath("$.address_line_1").doesNotExist())
        .andExpect(jsonPath("$.operatorType").doesNotExist());
  }

  @Test
  void listResponseIsATopLevelObjectNeverABareArray() throws Exception {
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isMap())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(25))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Livestock Ltd"));
  }

  @Test
  void notFoundIsProblemJsonCarryingTheTraceId() throws Exception {
    String traceId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, "665f1c2ab3e4d51a2c9d0e77")
                .header(ORG_HEADER, ORGANISATION_ID)
                .header("x-cdp-request-id", traceId))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.traceId").value(traceId))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void validationProblemCarriesCamelCaseErrorsMapAndTraceId() throws Exception {
    String traceId = UUID.randomUUID().toString();
    String badBody = CREATE_BODY.replace("\"14 Drover's Way\"", "\"\"");

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .header("x-cdp-request-id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBody))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.traceId").value(traceId))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.address_line_1").doesNotExist());
  }

  // ---- generated document lock ------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void apiDocsCarryTheWholeCamelCaseAndAnyOfContractSurface() {
    Map<String, Object> doc = fetchApiDocs();
    Map<String, Object> schemas = (Map<String, Object>) nested(doc, "components", "schemas");

    // every wire property across every schema is camelCase (no snake_case leak)
    schemas.forEach(
        (schemaName, schema) -> {
          Object properties = ((Map<String, Object>) schema).get("properties");
          if (properties instanceof Map<?, ?> props) {
            props
                .keySet()
                .forEach(
                    key ->
                        assertThat((String) key)
                            .as("wire property %s.%s must be camelCase", schemaName, key)
                            .matches("[a-z][a-zA-Z0-9]*"));
          }
        });

    // the untyped model carries no operatorType/transporterCategory/status enum on the wire
    Map<String, Object> requestProps =
        (Map<String, Object>) nested(schemas, "AddressRequest", "properties");
    assertThat(requestProps).doesNotContainKeys("operatorType", "transporterCategory");
    Map<String, Object> responseProps =
        (Map<String, Object>) nested(schemas, "OperatorResponse", "properties");
    assertThat(responseProps).doesNotContainKey("status");
    assertThat(responseProps).containsKey("deleted");

    // the list schema is a top-level object with an items array, never a bare array (OpenAPI 3.1
    // omits `type: object` on a schema that declares properties, so pin the absence of a bare array)
    Map<String, Object> page = pageSchema(schemas);
    assertThat(page).doesNotContainEntry("type", "array");
    Map<String, Object> pageProps = (Map<String, Object>) page.get("properties");
    assertThat(pageProps).containsKeys("items", "page", "pageSize", "totalItems", "totalPages");
    assertThat(nested(pageProps, "items", "type")).isEqualTo("array");
    // and the list operation returns that object by reference, not an inline array
    assertThat(listResponseSchemaRef(doc)).endsWith("/OperatorPageResponse");

    // both problem branches registered as component schemas
    assertThat(schemas).containsKeys("Problem", "ValidationProblem");

    // POST and PUT 400 are anyOf(ValidationProblem, Problem) — NOT oneOf
    assertAnyOfProblem(doc, "/organisation/{orgId}/addresses", "post");
    assertAnyOfProblem(doc, "/organisation/{orgId}/addresses/{operator-id}", "put");
  }

  @Test
  @SuppressWarnings("unchecked")
  void apiDocsMatchTheCommittedArtifactAndTheLockedContract() throws IOException {
    Map<String, Object> live = fetchApiDocs();
    live.remove("servers");
    String rendered = yaml().dump(live);

    if (Boolean.getBoolean("openapi.generate")) {
      Files.writeString(GENERATED_DOC, rendered);
      return;
    }

    // staleness gate: the committed artifact must be regenerated when the API changes
    assertThat(Files.exists(GENERATED_DOC))
        .as("docs/openapi/operators.yml must be committed — regenerate with -Dopenapi.generate=true")
        .isTrue();
    assertThat(Files.readString(GENERATED_DOC))
        .as("committed docs/openapi/operators.yml is stale against /v3/api-docs")
        .isEqualTo(rendered);

    // divergence gate: the generated surface must match the in-repo locked contract
    Map<String, Object> locked = yaml().load(Files.readString(LOCKED_CONTRACT));
    Map<String, Object> livePaths = (Map<String, Object>) live.get("paths");
    Map<String, Object> lockedPaths = (Map<String, Object>) locked.get("paths");

    assertThat(livePaths.keySet())
        .as("path set must match the locked contract")
        .isEqualTo(lockedPaths.keySet());

    lockedPaths.forEach(
        (path, lockedOps) -> {
          Map<String, Object> liveOps = (Map<String, Object>) livePaths.get(path);
          Map<String, Object> lockedOpMap = (Map<String, Object>) lockedOps;
          assertThat(httpMethods(liveOps))
              .as("HTTP methods on %s must match the locked contract", path)
              .isEqualTo(httpMethods(lockedOpMap));
          httpMethods(lockedOpMap)
              .forEach(
                  method -> {
                    Object lockedId = ((Map<String, Object>) lockedOpMap.get(method)).get("operationId");
                    Object liveId = ((Map<String, Object>) liveOps.get(method)).get("operationId");
                    assertThat(liveId)
                        .as("operationId for %s %s must match the locked contract", method, path)
                        .isEqualTo(lockedId);
                  });
        });

    // the locked contract itself declares the anyOf 400 — the pin the whole design leans on
    assertAnyOfProblem(locked, "/organisation/{orgId}/addresses", "post");
    assertAnyOfProblem(locked, "/organisation/{orgId}/addresses/{operator-id}", "put");
  }

  // ---- helpers ----------------------------------------------------------------------------

  /**
   * The live generated spec, fetched as JSON and parsed with a plain Jackson mapper (robust to any
   * charset in the response) into an ordered map — the same document springdoc renders at
   * {@code /v3/api-docs.yaml}.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchApiDocs() {
    String json =
        RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/v3/api-docs")
            .retrieve()
            .body(String.class);
    try {
      return new ObjectMapper().readValue(json, Map.class);
    } catch (IOException e) {
      throw new AssertionError("could not parse /v3/api-docs", e);
    }
  }

  /** Deterministic block-style YAML — the committed artifact is a byte-stable render of the spec. */
  private static Yaml yaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setSplitLines(false);
    return new Yaml(options);
  }

  private static final java.util.Set<String> HTTP_METHODS =
      java.util.Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

  /** The HTTP-method keys of a path item, ignoring path-level {@code parameters}/{@code summary}. */
  private static java.util.Set<String> httpMethods(Map<String, Object> pathItem) {
    return pathItem.keySet().stream().filter(HTTP_METHODS::contains).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
  }

  @SuppressWarnings("unchecked")
  private static String listResponseSchemaRef(Map<String, Object> doc) {
    Map<String, Object> content =
        (Map<String, Object>)
            nested(doc, "paths", "/organisation/{orgId}/addresses", "get", "responses", "200", "content");
    Map<String, Object> mediaType = (Map<String, Object>) content.values().iterator().next();
    return (String) ((Map<String, Object>) mediaType.get("schema")).get("$ref");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> pageSchema(Map<String, Object> schemas) {
    return schemas.entrySet().stream()
        .filter(e -> e.getKey().contains("Page"))
        .map(e -> (Map<String, Object>) e.getValue())
        .filter(schema -> ((Map<String, Object>) schema.getOrDefault("properties", Map.of()))
            .containsKey("items"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no top-level page/list object schema found"));
  }

  @SuppressWarnings("unchecked")
  private static void assertAnyOfProblem(Map<String, Object> doc, String path, String method) {
    Map<String, Object> schema =
        (Map<String, Object>)
            nested(
                doc,
                "paths",
                path,
                method,
                "responses",
                "400",
                "content",
                "application/problem+json",
                "schema");
    assertThat(schema)
        .as("%s %s 400 must be anyOf, not oneOf", method, path)
        .containsKey("anyOf")
        .doesNotContainKey("oneOf");
    List<Map<String, Object>> branches = (List<Map<String, Object>>) schema.get("anyOf");
    assertThat(branches).hasSize(2);
    assertThat(branches.stream().map(b -> (String) b.get("$ref")).toList())
        .as("%s %s 400 anyOf must reference ValidationProblem and Problem", method, path)
        .anyMatch(ref -> ref != null && ref.endsWith("/ValidationProblem"))
        .anyMatch(ref -> ref != null && ref.endsWith("/Problem"));
  }

  @SuppressWarnings("unchecked")
  private static Object nested(Map<String, Object> root, String... keys) {
    Object current = root;
    for (String key : keys) {
      current = ((Map<String, Object>) current).get(key);
      if (current == null) {
        throw new AssertionError("missing key '" + key + "' while navigating " + String.join(".", keys));
      }
    }
    return current;
  }
}
