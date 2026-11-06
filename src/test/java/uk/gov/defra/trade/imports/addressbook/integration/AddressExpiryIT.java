package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import org.springframework.test.context.TestPropertySource;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressExpiryIndex;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

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
    Document ttlIndex =
        mongoTemplate.getCollection("addresses").listIndexes().into(new ArrayList<>()).stream()
            .filter(index -> TTL_INDEX.equals(index.getString("name")))
            .findFirst()
            .orElseThrow();

    assertThat(ttlIndex.get("key", Document.class)).isEqualTo(new Document("expireAt", 1));
    assertThat(ttlIndex.get("expireAfterSeconds", Number.class).longValue()).isZero();
  }

  @Test
  void post_givesTheNewAddressAnExpireAtSevenDaysAhead() throws Exception {
    String body = addressBody().json();
    Instant beforeAtMongoPrecision = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    postAddress(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.expireAt").doesNotExist());

    Instant after = Instant.now();
    List<Address> addresses = activeAddressesFor(ORGANISATION_ID);
    assertThat(addresses).hasSize(1);
    assertThat(addresses.getFirst().getExpireAt())
        .isBetween(beforeAtMongoPrecision.plus(7, ChronoUnit.DAYS), after.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void expiredAddresses_areRemovedByMongoWhileUndatedAndUnexpiredOnesStay() {
    Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
    Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
    String expiredActive = save("Expired Farm", AddressStatus.ACTIVE, past);
    String expiredTombstone = save("Expired Tombstone", AddressStatus.DELETED, past);
    String undated = save("Undated Farm", AddressStatus.ACTIVE, null);
    String unexpired = save("Recent Farm", AddressStatus.ACTIVE, future);

    setTtlMonitorSleepSecs(1);

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
    return saveAddress(addressBuilder(ORGANISATION_ID, status).name(name).expireAt(expireAt))
        .getId();
  }

  private void setTtlMonitorSleepSecs(int seconds) {
    mongoClient
        .getDatabase("admin")
        .runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", seconds));
  }
}
