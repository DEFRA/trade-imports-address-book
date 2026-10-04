package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.MongoClient;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressExpiryIndex;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

/**
 * Non-prod address expiry against a real Mongo: a created address gets an {@code expireAt}, and
 * Mongo's TTL monitor removes expired addresses (live or tombstoned) while leaving undated and
 * not-yet-expired ones alone.
 *
 * <p>The Mongo container is shared by every integration test, so the TTL index is dropped after
 * each test — {@code OperatorIndexIT} pins the default index set. The TTL monitor is sped up from
 * its 60-second default so a removal can be observed within the test.
 */
@TestPropertySource(
    properties = {"address-book.ttl.days=7", "address-book.ttl.expiry.enabled=true"})
class AddressExpiryIT extends IntegrationBase {

  private static final String TTL_INDEX = "address_expire_at_ttl";

  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private MongoClient mongoClient;
  @Autowired private AddressExpiryIndex addressExpiryIndex;

  @BeforeEach
  void ensureTtlIndex() {
    addressExpiryIndex.createIndex();
  }

  @AfterEach
  void restoreSharedMongo() {
    mongoTemplate.indexOps(Address.class).dropIndex(TTL_INDEX);
    setTtlMonitorSleepSecs(60);
  }

  @Test
  void ttlIndex_isOnExpireAtAndExpiresDocumentsAtThatTime() {
    // When
    Document ttlIndex =
        mongoTemplate.getCollection("addresses").listIndexes().into(new ArrayList<>()).stream()
            .filter(index -> TTL_INDEX.equals(index.getString("name")))
            .findFirst()
            .orElseThrow();

    // Then
    assertThat(ttlIndex.get("key", Document.class)).isEqualTo(new Document("expireAt", 1));
    assertThat(ttlIndex.get("expireAfterSeconds", Number.class).longValue()).isZero();
  }

  @Test
  void post_givesTheNewAddressAnExpireAtSevenDaysAhead() throws Exception {
    // Given
    String body =
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
    Instant before = Instant.now();

    // When
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.expireAt").doesNotExist());

    // Then
    Instant after = Instant.now();
    List<Address> addresses = activeAddressesFor(ORGANISATION_ID);
    assertThat(addresses).hasSize(1);
    assertThat(addresses.getFirst().getExpireAt())
        .isBetween(before.plus(7, ChronoUnit.DAYS), after.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void expiredAddresses_areRemovedByMongoWhileUndatedAndUnexpiredOnesStay() {
    // Given
    Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
    Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
    String expiredActive = save("Expired Farm", AddressStatus.ACTIVE, past);
    String expiredTombstone = save("Expired Tombstone", AddressStatus.DELETED, past);
    String undated = save("Created Before Expiry", AddressStatus.ACTIVE, null);
    String unexpired = save("Recent Farm", AddressStatus.ACTIVE, future);

    // When
    setTtlMonitorSleepSecs(1);

    // Then
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(mongoTemplate.findById(expiredActive, Address.class)).isNull();
              assertThat(mongoTemplate.findById(expiredTombstone, Address.class)).isNull();
            });
    assertThat(mongoTemplate.findById(undated, Address.class)).isNotNull();
    assertThat(mongoTemplate.findById(unexpired, Address.class)).isNotNull();
  }

  private String save(String name, AddressStatus status, Instant expireAt) {
    Address address =
        Address.builder()
            .name(name)
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .organisationId(ORGANISATION_ID)
            .status(status)
            .expireAt(expireAt)
            .build();
    return operatorRepository.save(address).getId();
  }

  private void setTtlMonitorSleepSecs(int seconds) {
    mongoClient
        .getDatabase("admin")
        .runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", seconds));
  }
}
