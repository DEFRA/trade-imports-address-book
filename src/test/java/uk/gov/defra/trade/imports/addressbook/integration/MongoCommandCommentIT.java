package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.MongoDatabase;
import java.util.HashSet;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoCommandCommentIT extends IntegrationBase {

  private static final String TRACE_HEADER = "x-cdp-request-id";
  private static final String TRACE_ID = "trace-from-the-caller";

  @Autowired private MongoTemplate mongoTemplate;

  @BeforeEach
  void profileEveryCommand() {
    database().runCommand(new Document("profile", 2));
  }

  @AfterEach
  void stopProfiling() {
    database().runCommand(new Document("profile", 0));
    database().getCollection("system.profile").drop();
  }

  @Test
  void eachCommandOfARequestReachesTheServerWithTheRequestsTraceIdAsItsComment()
      throws Exception {
    String addressId =
        createdResourceId(
            mockMvc.perform(
                postAddressRequest(addressBody().json()).header(TRACE_HEADER, TRACE_ID)));
    mockMvc
        .perform(getAddressRequest(addressId).header(TRACE_HEADER, TRACE_ID))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            putAddressRequest(addressId, addressBody().json()).header(TRACE_HEADER, TRACE_ID))
        .andExpect(status().isOk());
    mockMvc
        .perform(listAddressesRequest("pageSize", "1").header(TRACE_HEADER, TRACE_ID))
        .andExpect(status().isOk());

    assertThat(profiledOperationsCommentedWith(TRACE_ID))
        .containsExactlyInAnyOrder("insert", "query", "update", "command");
  }

  @Test
  void aCommandRunOutsideARequestReachesTheServerWithNoComment() {
    mongoTemplate.insert(new Document("name", "Farm"), "address");

    Document insert =
        database()
            .getCollection("system.profile")
            .find(new Document("op", "insert").append("ns", database().getName() + ".address"))
            .first();

    assertThat(insert).isNotNull();
    assertThat(insert.get("command", Document.class).containsKey("comment")).isFalse();
  }

  private Set<String> profiledOperationsCommentedWith(String comment) {
    Set<String> operations = new HashSet<>();
    database()
        .getCollection("system.profile")
        .find(new Document("command.comment", comment))
        .forEach(entry -> operations.add(entry.getString("op")));
    return operations;
  }

  private MongoDatabase database() {
    return mongoTemplate.getDb();
  }
}
