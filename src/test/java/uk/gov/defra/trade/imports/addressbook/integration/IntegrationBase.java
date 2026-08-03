package uk.gov.defra.trade.imports.addressbook.integration;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
abstract class IntegrationBase {

  protected static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  protected static final String ORGANISATION_ID = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";

  @LocalServerPort
  int port;

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  protected OperatorRepository operatorRepository;

  protected List<Address> activeAddressesFor(String organisationId) {
    return operatorRepository
        .findByOrganisationIdAndStatus(
            organisationId, AddressStatus.ACTIVE, PageRequest.of(0, 100))
        .getContent();
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
    mongoTemplate.getDb().listCollectionNames().forEach(mongoTemplate::dropCollection);
  }
}
