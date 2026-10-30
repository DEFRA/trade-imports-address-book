package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
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
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

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

  protected static final String VALIDATION_ERROR_TYPE =
      "https://api.cdp.defra.cloud/problems/validation-error";

  protected static final String NAME = "Highland Livestock Ltd";
  protected static final String ADDRESS_LINE_1 = "14 Drover's Way";
  protected static final String TOWN_OR_CITY = "Inverness";
  protected static final String POSTCODE = "IV2 3JH";
  protected static final String COUNTRY_CODE = "GB";
  protected static final String PHONE = "+44 1463 234567";
  protected static final String EMAIL = "exports@highlandlivestock.example.com";

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
  protected OperatorRepository operatorRepository;

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

  protected static AddressBody addressBody() {
    return new AddressBody();
  }

  protected Address saveAddress(Address.AddressBuilder address) {
    return operatorRepository.save(address.build());
  }

  protected List<Address> activeAddressesFor(String organisationId) {
    return operatorRepository
        .findByOrganisationIdAndStatus(
            organisationId, AddressStatus.ACTIVE, PageRequest.of(0, 100))
        .getContent();
  }

  protected static MockHttpServletRequestBuilder postAddressRequest(String json) {
    return post("/organisation/{orgId}/addresses", ORGANISATION_ID)
        .header(ORG_HEADER, ORGANISATION_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  protected ResultActions postAddress(AddressBody body) throws Exception {
    return postAddress(body.json());
  }

  protected ResultActions postAddress(String json) throws Exception {
    return mockMvc.perform(postAddressRequest(json));
  }

  protected ResultActions getAddress(String addressId) throws Exception {
    return mockMvc.perform(
        get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, addressId)
            .header(ORG_HEADER, ORGANISATION_ID));
  }

  protected String createdAddressId(ResultActions created) throws Exception {
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

  protected static final class AddressBody {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> fields = new LinkedHashMap<>();

    private AddressBody() {
      fields.put("name", NAME);
      fields.put("addressLine1", ADDRESS_LINE_1);
      fields.put("townOrCity", TOWN_OR_CITY);
      fields.put("postcode", POSTCODE);
      fields.put("countryCode", COUNTRY_CODE);
      fields.put("phone", PHONE);
      fields.put("email", EMAIL);
    }

    AddressBody with(String field, Object value) {
      fields.put(field, value);
      return this;
    }

    AddressBody blank(String field) {
      return with(field, "");
    }

    String json() {
      try {
        return MAPPER.writeValueAsString(fields);
      } catch (JsonProcessingException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
