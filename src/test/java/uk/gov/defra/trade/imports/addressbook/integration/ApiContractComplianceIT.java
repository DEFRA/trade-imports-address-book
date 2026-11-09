package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.addressbook.ListPaging.DEFAULT_PAGE_SIZE;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ApiDocs.lockedContract;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ApiDocs.nested;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ApiDocs.requiredProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.defra.trade.imports.addressbook.testsupport.ApiDocs;

class ApiContractComplianceIT extends IntegrationBase {

  @Test
  void createAndReadEmitCamelCasePropertiesAndADerivedDeletedBoolean() throws Exception {
    ResultActions created = postAddress(addressBody());

    expectCamelCaseLiveAddress(created.andExpect(status().isCreated()));
    expectCamelCaseLiveAddress(getAddress(createdResourceId(created)).andExpect(status().isOk()));
  }

  private static void expectCamelCaseLiveAddress(ResultActions address) throws Exception {
    address
        .andExpect(jsonPath("$.addressLine1").value(ADDRESS_LINE_1))
        .andExpect(jsonPath("$.townOrCity").value(TOWN_OR_CITY))
        .andExpect(jsonPath("$.countryCode").value(COUNTRY_CODE))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.modifiedAt").exists())
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.address_line_1").doesNotExist());
  }

  @Test
  void listResponseIsATopLevelObjectNeverABareArray() throws Exception {
    postAddress(addressBody()).andExpect(status().isCreated());

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isMap())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(DEFAULT_PAGE_SIZE))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.items[0].name").value(NAME));
  }

  @Test
  void notFoundIsProblemJsonCarryingTheTraceId() throws Exception {
    String traceId = UUID.randomUUID().toString();

    mockMvc
        .perform(getAddressRequest(UNKNOWN_ID).header("x-cdp-request-id", traceId))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.traceId").value(traceId))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void validationProblemCarriesCamelCaseErrorsMapAndTraceId() throws Exception {
    String traceId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            postAddressRequest(addressBody().blank("addressLine1").json())
                .header("x-cdp-request-id", traceId))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.traceId").value(traceId))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.address_line_1").doesNotExist());
  }

  @Test
  @SuppressWarnings("unchecked")
  void apiDocsListSchemaIsATopLevelObjectReturnedByReference() {
    Map<String, Object> doc = ApiDocs.fetchLive(port);
    Map<String, Object> page = pageSchema((Map<String, Object>) nested(doc, "components", "schemas"));

    Map<String, Object> pageProps = (Map<String, Object>) page.get("properties");

    assertThat(page).doesNotContainEntry("type", "array");
    assertThat(pageProps).containsKeys("items", "page", "pageSize", "totalItems", "totalPages");
    assertThat(nested(pageProps, "items", "type")).isEqualTo("array");
    assertThat(listResponseSchemaRef(doc)).endsWith("/AddressPageResponse");
  }

  @Test
  void committedArtifactIsNotStaleAgainstTheLiveApiDocs() throws IOException {
    assertThat(Files.exists(ApiDocs.COMMITTED_ARTIFACT))
        .as("the OpenAPI artifact must be committed — regenerate with -Dopenapi.generate=true")
        .isTrue();
    assertThat(Files.readString(ApiDocs.COMMITTED_ARTIFACT))
        .as("the committed OpenAPI artifact is stale against /v3/api-docs")
        .isEqualTo(ApiDocs.renderCommittedForm(ApiDocs.fetchLive(port)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void liveApiDocsHaveTheLockedPathsHttpMethodsAndOperationIds() throws IOException {
    Map<String, Object> live = ApiDocs.fetchLive(port);
    Map<String, Object> livePaths = (Map<String, Object>) live.get("paths");
    Map<String, Object> lockedPaths = (Map<String, Object>) lockedContract().get("paths");

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
                    Object lockedId =
                        ((Map<String, Object>) lockedOpMap.get(method)).get("operationId");
                    Object liveId = ((Map<String, Object>) liveOps.get(method)).get("operationId");
                    assertThat(liveId)
                        .as("operationId for %s %s must match the locked contract", method, path)
                        .isEqualTo(lockedId);
                  });
        });
  }

  @Test
  void liveApiDocsHaveTheLockedSchemaPropertyNames() throws IOException {
    assertSchemaPropertyNamesMatch(ApiDocs.fetchLive(port), lockedContract());
  }

  @Test
  void liveApiDocsHaveTheLockedOperationParameters() throws IOException {
    assertOperationParametersMatch(ApiDocs.fetchLive(port), lockedContract());
  }

  @Test
  void liveApiDocsHaveTheLockedAnyOfProblemOnPostAndPutBadRequest() throws IOException {
    Map<String, Object> live = ApiDocs.fetchLive(port);
    Map<String, Object> locked = lockedContract();

    assertAnyOfProblem(live, locked, "/organisation/{orgId}/addresses", "post");
    assertAnyOfProblem(live, locked, "/organisation/{orgId}/addresses/{address-id}", "put");
    assertAnyOfProblem(live, locked, "/organisation/{orgId}/transporters", "post");
    assertAnyOfProblem(live, locked, "/organisation/{orgId}/transporters/{transporter-id}", "put");
  }

  @Test
  void liveApiDocsHaveTheLockedAllowedValuesForRolesAndTheListFilterParameters()
      throws IOException {
    assertListAndSchemaAllowedValuesMatchLocked(
        ApiDocs.fetchLive(port),
        lockedContract(),
        "/organisation/{orgId}/addresses",
        List.of("role", "searchRole"),
        List.of("AddressRequest", "AddressResponse"),
        "roles");
  }

  @Test
  void liveApiDocsHaveTheLockedAllowedValuesForTheTransporterTypeAndTheListTypeParameters()
      throws IOException {
    assertListAndSchemaAllowedValuesMatchLocked(
        ApiDocs.fetchLive(port),
        lockedContract(),
        "/organisation/{orgId}/transporters",
        List.of("type", "searchType"),
        List.of("TransporterRequest", "TransporterResponse"),
        "type");
  }

  private static void assertListAndSchemaAllowedValuesMatchLocked(
      Map<String, Object> live,
      Map<String, Object> locked,
      String path,
      List<String> listParameters,
      List<String> schemas,
      String property) {
    for (String parameter : listParameters) {
      assertThat(listParameterAllowedValues(locked, path, parameter))
          .as("locked list %s parameter must list its allowed values", parameter)
          .isNotEmpty();
      assertThat(listParameterAllowedValues(live, path, parameter))
          .as("allowed values of the list %s parameter must match the locked contract", parameter)
          .isEqualTo(listParameterAllowedValues(locked, path, parameter));
    }

    for (String schema : schemas) {
      assertThat(allowedValues(locked, schema, property))
          .as("locked %s.%s must list its allowed values", schema, property)
          .isNotEmpty();
      assertThat(allowedValues(live, schema, property))
          .as("allowed values of %s.%s must match the locked contract", schema, property)
          .isEqualTo(allowedValues(locked, schema, property));
    }
  }

  @Test
  void liveAddressRequestHasTheLockedRequiredProperties() throws IOException {
    List<Object> lockedRequired = requiredProperties(lockedContract(), "AddressRequest");

    assertThat(lockedRequired).contains("roles");
    assertThat(requiredProperties(ApiDocs.fetchLive(port), "AddressRequest"))
        .as("required properties of AddressRequest must match the locked contract")
        .containsExactlyInAnyOrderElementsOf(lockedRequired);
  }

  @Test
  void liveTransporterRequestHasTheLockedRequiredProperties() throws IOException {
    List<Object> lockedRequired = requiredProperties(lockedContract(), "TransporterRequest");

    assertThat(lockedRequired).contains("type");
    assertThat(requiredProperties(ApiDocs.fetchLive(port), "TransporterRequest"))
        .as("required properties of TransporterRequest must match the locked contract")
        .containsExactlyInAnyOrderElementsOf(lockedRequired);
  }

  @Test
  void liveAddressRequestRolesHasTheLockedMinimumNumberOfItems() throws IOException {
    Object lockedMinItems = minItems(lockedContract(), "AddressRequest", "roles");

    assertThat(lockedMinItems).isEqualTo(1);
    assertThat(minItems(ApiDocs.fetchLive(port), "AddressRequest", "roles"))
        .as("minItems of AddressRequest.roles must match the locked contract")
        .isEqualTo(lockedMinItems);
  }

  private static Object minItems(Map<String, Object> doc, String schema, String property) {
    return nested(doc, "components", "schemas", schema, "properties", property, "minItems");
  }

  @SuppressWarnings("unchecked")
  private static List<Object> listParameterAllowedValues(
      Map<String, Object> doc, String path, String parameterName) {
    Map<String, Object> listOperation = (Map<String, Object>) nested(doc, "paths", path, "get");
    return ((List<Map<String, Object>>) listOperation.get("parameters"))
        .stream()
            .filter(parameter -> parameterName.equals(parameter.get("name")))
            .map(parameter -> enumOf(doc, (Map<String, Object>) parameter.get("schema")))
            .findFirst()
            .orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> allowedValues(
      Map<String, Object> doc, String schema, String property) {
    return enumOf(
        doc,
        (Map<String, Object>) nested(doc, "components", "schemas", schema, "properties", property));
  }

  @SuppressWarnings("unchecked")
  private static List<Object> enumOf(Map<String, Object> doc, Map<String, Object> schema) {
    Map<String, Object> valueSchema =
        "array".equals(schema.get("type")) ? (Map<String, Object>) schema.get("items") : schema;
    Object ref = valueSchema.get("$ref");
    if (ref != null) {
      String componentName = ((String) ref).substring("#/components/schemas/".length());
      valueSchema = (Map<String, Object>) nested(doc, "components", "schemas", componentName);
    }
    return (List<Object>) valueSchema.get("enum");
  }

  @SuppressWarnings("unchecked")
  private static void assertSchemaPropertyNamesMatch(
      Map<String, Object> live, Map<String, Object> locked) {
    Map<String, Object> liveComponents = (Map<String, Object>) live.get("components");
    Map<String, Object> lockedComponents = (Map<String, Object>) locked.get("components");
    assertThat(liveComponents)
        .as("live /v3/api-docs must declare components")
        .isNotNull();
    assertThat(lockedComponents)
        .as("locked contract must declare components")
        .isNotNull();

    Map<String, Object> liveSchemaMap = (Map<String, Object>) liveComponents.get("schemas");
    Map<String, Object> lockedSchemaMap = (Map<String, Object>) lockedComponents.get("schemas");
    assertThat(liveSchemaMap)
        .as("live /v3/api-docs must declare component schemas")
        .isNotNull();
    assertThat(lockedSchemaMap)
        .as("locked contract must declare component schemas")
        .isNotNull();
    assertThat(liveSchemaMap.keySet())
        .as("component schema names must match the locked contract")
        .isEqualTo(lockedSchemaMap.keySet());
    liveSchemaMap.forEach(
        (name, liveSchema) -> {
          Object liveEnum = ((Map<String, Object>) liveSchema).get("enum");
          if (liveEnum != null) {
            assertThat(liveEnum)
                .as("enum values of schema %s must match the locked contract", name)
                .isEqualTo(((Map<String, Object>) lockedSchemaMap.get(name)).get("enum"));
            return;
          }
          Object liveProps = ((Map<String, Object>) liveSchema).get("properties");
          Object lockedProps = ((Map<String, Object>) lockedSchemaMap.get(name)).get("properties");
          assertThat(liveProps)
              .as("schema %s must declare properties on live /v3/api-docs", name)
              .isInstanceOf(Map.class);
          assertThat(lockedProps)
              .as("schema %s must declare properties on the locked contract", name)
              .isInstanceOf(Map.class);
          assertThat(((Map<?, ?>) liveProps).keySet())
              .as("property names on schema %s must match the locked contract", name)
              .isEqualTo(((Map<?, ?>) lockedProps).keySet());
        });
  }

  private static Set<String> httpMethods(Map<String, Object> pathItem) {
    Set<String> httpMethodNames =
        Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");
    return pathItem.keySet().stream()
        .filter(httpMethodNames::contains)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @SuppressWarnings("unchecked")
  private static String listResponseSchemaRef(Map<String, Object> doc) {
    Map<String, Object> content =
        (Map<String, Object>)
            nested(
                doc,
                "paths",
                "/organisation/{orgId}/addresses",
                "get",
                "responses",
                "200",
                "content");
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
  private static void assertOperationParametersMatch(
      Map<String, Object> live, Map<String, Object> locked) {
    Map<String, Object> livePaths = (Map<String, Object>) live.get("paths");
    Map<String, Object> lockedPaths = (Map<String, Object>) locked.get("paths");
    lockedPaths.forEach(
        (path, lockedOps) -> {
          Map<String, Object> liveOps = (Map<String, Object>) livePaths.get(path);
          Map<String, Object> lockedOpMap = (Map<String, Object>) lockedOps;
          httpMethods(lockedOpMap)
              .forEach(
                  method -> {
                    List<Map<String, Object>> liveParameterNamesAndLocations =
                        parameterNamesAndLocations(liveOps.get(method));
                    List<Map<String, Object>> lockedParameterNamesAndLocations =
                        parameterNamesAndLocations(lockedOpMap.get(method));
                    assertThat(liveParameterNamesAndLocations)
                        .as("parameters for %s %s must match the locked contract", method, path)
                        .isEqualTo(lockedParameterNamesAndLocations);
                  });
        });
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> parameterNamesAndLocations(Object operation) {
    if (!(operation instanceof Map<?, ?> operationMap)) {
      return List.of();
    }
    Object parameters = operationMap.get("parameters");
    if (!(parameters instanceof List<?> parameterList)) {
      return List.of();
    }
    return parameterList.stream()
        .map(
            parameter -> {
              Map<String, Object> map = (Map<String, Object>) parameter;
              return Map.<String, Object>of("name", map.get("name"), "in", map.get("in"));
            })
        .toList();
  }

  private static void assertAnyOfProblem(
      Map<String, Object> live, Map<String, Object> locked, String path, String method) {
    List<String> liveRefs = anyOfBranchRefs(live, path, method);
    List<String> lockedRefs = anyOfBranchRefs(locked, path, method);
    assertThat(liveRefs)
        .as("%s %s 400 anyOf branch refs must match the locked contract", method, path)
        .isEqualTo(lockedRefs);
    assertThat(liveRefs)
        .as("%s %s 400 anyOf must reference ValidationProblem and Problem", method, path)
        .anyMatch(ref -> ref.endsWith("/ValidationProblem"))
        .anyMatch(ref -> ref.endsWith("/Problem"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> anyOfBranchRefs(
      Map<String, Object> doc, String path, String method) {
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
    return branches.stream().map(b -> (String) b.get("$ref")).sorted().toList();
  }
}
