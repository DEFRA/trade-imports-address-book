package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.NO_LIST_CRITERIA;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.defra.trade.imports.addressbook.Application;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRole;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.AddressRepository;
import uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport;
import uk.gov.defra.trade.imports.addressbook.transporter.Transporter;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterRepository;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport;

@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
abstract class IntegrationBase {

  protected static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  protected static final String ORGANISATION_ID = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  protected static final String OTHER_ORG = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";
  protected static final String UNKNOWN_ID = "665f1c2ab3e4d51a2c9d0e77";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String ADDRESSES_PATH = "/organisation/{orgId}/addresses";
  private static final String ADDRESS_PATH = ADDRESSES_PATH + "/{address-id}";
  private static final String TRANSPORTERS_PATH = "/organisation/{orgId}/transporters";
  private static final String TRANSPORTER_PATH = TRANSPORTERS_PATH + "/{transporter-id}";

  protected static final String VALIDATION_ERROR_TYPE =
      "https://api.cdp.defra.cloud/problems/validation-error";

  private static final TransporterListCriteria NO_TRANSPORTER_LIST_CRITERIA =
      new TransporterListCriteria(List.of(), List.of());

  protected static final String NAME = AddressTestSupport.SAMPLE_NAME;
  protected static final String ADDRESS_LINE_1 = AddressTestSupport.SAMPLE_ADDRESS_LINE_1;
  protected static final String TOWN_OR_CITY = AddressTestSupport.SAMPLE_TOWN_OR_CITY;
  protected static final String POSTCODE = AddressTestSupport.SAMPLE_POSTCODE;
  protected static final String COUNTRY_CODE = AddressTestSupport.SAMPLE_COUNTRY_CODE;
  protected static final String PHONE = AddressTestSupport.SAMPLE_PHONE;
  protected static final String EMAIL = AddressTestSupport.SAMPLE_EMAIL;
  protected static final String NORTHERN_IRELAND_COUNTRY_CODE =
      TransporterTestSupport.NORTHERN_IRELAND_COUNTRY_CODE;
  protected static final String APPROVAL_NUMBER = TransporterTestSupport.SAMPLE_APPROVAL_NUMBER;

  static MongoDBContainer MONGO_CONTAINER = new MongoDBContainer(
      DockerImageName.parse("mongo:7.0")).withExposedPorts(27017).withReplicaSet();

  static {
    Startables.deepStart(MONGO_CONTAINER).join();
  }

  @LocalServerPort
  int port;

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  protected AddressRepository addressRepository;

  @Autowired
  protected TransporterRepository transporterRepository;

  @DynamicPropertySource
  static void setProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
    registry.add("spring.data.mongodb.ssl.enabled", () -> "false");
  }

  @BeforeEach
  void cleanDatabase() {
    mongoTemplate
        .getDb()
        .listCollectionNames()
        .forEach(name -> mongoTemplate.getCollection(name).deleteMany(new Document()));
  }

  protected static Address.AddressBuilder addressBuilder(
      String organisationId, AddressStatus status) {
    return Address.builder()
        .name(NAME)
        .addressLine1(ADDRESS_LINE_1)
        .townOrCity(TOWN_OR_CITY)
        .postcode(POSTCODE)
        .countryCode(COUNTRY_CODE)
        .phone(PHONE)
        .email(EMAIL)
        .organisationId(organisationId)
        .status(status);
  }

  protected static Address.AddressBuilder addressHolding(
      String organisationId, AddressRole... roles) {
    return addressBuilder(organisationId, AddressStatus.ACTIVE).roles(List.of(roles));
  }

  protected static RequestBody addressBody() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("name", NAME);
    fields.put("addressLine1", ADDRESS_LINE_1);
    fields.put("townOrCity", TOWN_OR_CITY);
    fields.put("postcode", POSTCODE);
    fields.put("countryCode", COUNTRY_CODE);
    fields.put("phone", PHONE);
    fields.put("email", EMAIL);
    fields.put("roles", List.of("PLACE_OF_ORIGIN"));
    return new RequestBody(fields);
  }

  protected Address saveAddress(Address.AddressBuilder address) {
    return addressRepository.save(address.build());
  }

  protected long storedCount(Class<?> documentType) {
    return mongoTemplate.count(new Query(), documentType);
  }

  protected List<Address> activeAddressesFor(String organisationId) {
    return addressRepository
        .listLiveAddresses(organisationId, NO_LIST_CRITERIA, PageRequest.of(0, 100))
        .getContent();
  }

  protected static RequestBody transporterBody() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("name", NAME);
    fields.put("addressLine1", ADDRESS_LINE_1);
    fields.put("townOrCity", TOWN_OR_CITY);
    fields.put("postcode", POSTCODE);
    fields.put("countryCode", COUNTRY_CODE);
    fields.put("phone", PHONE);
    fields.put("email", EMAIL);
    fields.put("type", "PRIVATE");
    return new RequestBody(fields);
  }

  protected static RequestBody commercialTransporterBody() {
    return transporterBody()
        .with("type", "COMMERCIAL")
        .with("countryCode", NORTHERN_IRELAND_COUNTRY_CODE)
        .with("approvalNumber", APPROVAL_NUMBER);
  }

  protected List<Transporter> activeTransportersFor(String organisationId) {
    return transporterRepository
        .listLiveTransporters(organisationId, NO_TRANSPORTER_LIST_CRITERIA, PageRequest.of(0, 100))
        .getContent();
  }

  protected ResultActions postTransporterAs(
      String orgId, String callerOrganisationId, RequestBody body) throws Exception {
    return mockMvc.perform(
        withJson(
            addressesRequest(HttpMethod.POST, callerOrganisationId, TRANSPORTERS_PATH, orgId),
            body.json()));
  }

  protected ResultActions postTransporter(RequestBody body) throws Exception {
    return postTransporterAs(ORGANISATION_ID, ORGANISATION_ID, body);
  }

  protected ResultActions getTransporterAs(
      String orgId, String callerOrganisationId, String transporterId) throws Exception {
    return mockMvc.perform(
        addressesRequest(
            HttpMethod.GET, callerOrganisationId, TRANSPORTER_PATH, orgId, transporterId));
  }

  protected ResultActions getTransporter(String transporterId) throws Exception {
    return getTransporterAs(ORGANISATION_ID, ORGANISATION_ID, transporterId);
  }

  protected ResultActions putTransporterAs(
      String orgId, String callerOrganisationId, String transporterId, RequestBody body)
      throws Exception {
    return mockMvc.perform(
        withJson(
            addressesRequest(
                HttpMethod.PUT, callerOrganisationId, TRANSPORTER_PATH, orgId, transporterId),
            body.json()));
  }

  protected ResultActions deleteTransporterAs(
      String orgId, String callerOrganisationId, String transporterId) throws Exception {
    return mockMvc.perform(
        addressesRequest(
            HttpMethod.DELETE, callerOrganisationId, TRANSPORTER_PATH, orgId, transporterId));
  }

  protected ResultActions deleteTransporter(String transporterId) throws Exception {
    return deleteTransporterAs(ORGANISATION_ID, ORGANISATION_ID, transporterId);
  }

  protected ResultActions listTransportersAs(String orgId, String callerOrganisationId)
      throws Exception {
    return mockMvc.perform(
        addressesRequest(HttpMethod.GET, callerOrganisationId, TRANSPORTERS_PATH, orgId));
  }

  protected ResultActions listTransporters(String... queryParameterNamesAndValues)
      throws Exception {
    MockHttpServletRequestBuilder request =
        addressesRequest(HttpMethod.GET, ORGANISATION_ID, TRANSPORTERS_PATH, ORGANISATION_ID);
    for (int i = 0; i < queryParameterNamesAndValues.length; i += 2) {
      request = request.param(queryParameterNamesAndValues[i], queryParameterNamesAndValues[i + 1]);
    }
    return mockMvc.perform(request);
  }

  protected static MockHttpServletRequestBuilder postAddressRequest(String json) {
    return withJson(
        addressesRequest(HttpMethod.POST, ORGANISATION_ID, ADDRESSES_PATH, ORGANISATION_ID), json);
  }

  protected ResultActions postAddress(RequestBody body) throws Exception {
    return postAddress(body.json());
  }

  protected ResultActions postAddress(String json) throws Exception {
    return mockMvc.perform(postAddressRequest(json));
  }

  protected ResultActions postAddressAs(String orgId, String callerOrganisationId, RequestBody body)
      throws Exception {
    return mockMvc.perform(
        withJson(
            addressesRequest(HttpMethod.POST, callerOrganisationId, ADDRESSES_PATH, orgId),
            body.json()));
  }

  protected ResultActions putAddress(String addressId, RequestBody body) throws Exception {
    return putAddress(addressId, body.json());
  }

  protected ResultActions putAddress(String addressId, String json) throws Exception {
    return putAddressAs(ORGANISATION_ID, ORGANISATION_ID, addressId, json);
  }

  protected ResultActions putAddressAs(
      String orgId, String callerOrganisationId, String addressId, RequestBody body)
      throws Exception {
    return putAddressAs(orgId, callerOrganisationId, addressId, body.json());
  }

  private ResultActions putAddressAs(
      String orgId, String callerOrganisationId, String addressId, String json) throws Exception {
    return mockMvc.perform(
        withJson(
            addressesRequest(HttpMethod.PUT, callerOrganisationId, ADDRESS_PATH, orgId, addressId),
            json));
  }

  protected ResultActions listAddresses(String... queryParameterNamesAndValues) throws Exception {
    MockHttpServletRequestBuilder request =
        addressesRequest(HttpMethod.GET, ORGANISATION_ID, ADDRESSES_PATH, ORGANISATION_ID);
    for (int i = 0; i < queryParameterNamesAndValues.length; i += 2) {
      request = request.param(queryParameterNamesAndValues[i], queryParameterNamesAndValues[i + 1]);
    }
    return mockMvc.perform(request);
  }

  protected ResultActions listAddressesAs(String orgId, String callerOrganisationId)
      throws Exception {
    return mockMvc.perform(
        addressesRequest(HttpMethod.GET, callerOrganisationId, ADDRESSES_PATH, orgId));
  }

  protected static Set<String> readIds(String body) throws Exception {
    Set<String> ids = new HashSet<>();
    JsonNode items = OBJECT_MAPPER.readTree(body).get("items");
    items.forEach(item -> ids.add(item.get("id").asText()));
    return ids;
  }

  protected static MockHttpServletRequestBuilder getAddressRequest(String addressId) {
    return addressesRequest(
        HttpMethod.GET, ORGANISATION_ID, ADDRESS_PATH, ORGANISATION_ID, addressId);
  }

  protected ResultActions getAddress(String addressId) throws Exception {
    return mockMvc.perform(getAddressRequest(addressId));
  }

  protected ResultActions getAddressAs(String orgId, String callerOrganisationId, String addressId)
      throws Exception {
    return mockMvc.perform(
        addressesRequest(HttpMethod.GET, callerOrganisationId, ADDRESS_PATH, orgId, addressId));
  }

  protected ResultActions deleteAddress(String addressId) throws Exception {
    return deleteAddressAs(ORGANISATION_ID, ORGANISATION_ID, addressId);
  }

  protected ResultActions deleteAddressAs(
      String orgId, String callerOrganisationId, String addressId) throws Exception {
    return mockMvc.perform(
        addressesRequest(
            HttpMethod.DELETE, callerOrganisationId, ADDRESS_PATH, orgId, addressId));
  }

  private static MockHttpServletRequestBuilder addressesRequest(
      HttpMethod method,
      @Nullable String callerOrganisationId,
      String path,
      Object... pathVariables) {
    MockHttpServletRequestBuilder request = request(method, path, pathVariables);
    return callerOrganisationId == null
        ? request
        : request.header(ORG_HEADER, callerOrganisationId);
  }

  private static MockHttpServletRequestBuilder withJson(
      MockHttpServletRequestBuilder request, String json) {
    return request.contentType(MediaType.APPLICATION_JSON).content(json);
  }

  protected String createdResourceId(ResultActions created) throws Exception {
    String location =
        created.andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    assertThat(location).isNotNull();
    return location.substring(location.lastIndexOf('/') + 1);
  }

  protected static ResultMatcher badRequestProblemWithNoErrors() {
    return ResultMatcher.matchAll(
        status().isBadRequest(),
        header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE),
        jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"),
        jsonPath("$.title").value("Bad Request"),
        jsonPath("$.status").value(400),
        jsonPath("$.errors").doesNotExist());
  }

  protected static final class RequestBody {

    private final Map<String, Object> fields;

    private RequestBody(Map<String, Object> seedFields) {
      fields = new LinkedHashMap<>(seedFields);
    }

    RequestBody without(String field) {
      fields.remove(field);
      return this;
    }

    RequestBody with(String field, Object value) {
      fields.put(field, value);
      return this;
    }

    RequestBody blank(String field) {
      return with(field, "");
    }

    String json() {
      try {
        return OBJECT_MAPPER.writeValueAsString(fields);
      } catch (JsonProcessingException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
