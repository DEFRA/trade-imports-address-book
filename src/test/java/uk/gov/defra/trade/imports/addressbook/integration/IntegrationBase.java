package uk.gov.defra.trade.imports.addressbook.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.test.web.servlet.ResultMatcher;
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

  @LocalServerPort
  int port;

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  protected OperatorRepository operatorRepository;

  protected static Address.AddressBuilder addressBuilder(
      String organisationId, AddressStatus status) {
    return Address.builder()
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .townOrCity("Inverness")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .organisationId(organisationId)
        .status(status);
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

  protected static ResultMatcher badRequestProblemWithNoErrors() {
    return ResultMatcher.matchAll(
        status().isBadRequest(),
        header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE),
        jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"),
        jsonPath("$.title").value("Bad Request"),
        jsonPath("$.status").value(400),
        jsonPath("$.errors").doesNotExist());
  }

  static MongoDBContainer MONGO_CONTAINER = new MongoDBContainer(
      DockerImageName.parse("mongo:7.0")).withExposedPorts(27017).withReplicaSet();

  static {
    Startables.deepStart(MONGO_CONTAINER).join();
  }

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
}
