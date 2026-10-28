package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorService.DEFAULT_PAGE_SIZE;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

class OperatorIndexIT extends IntegrationBase {

  @Autowired private MongoTemplate mongoTemplate;

  @Test
  void index_shouldDeclareOrgStatusCreatedAndUseItInExplainPlan() {
    // Given — auto-index-creation builds indexes at context startup
    List<IndexInfo> indexes = mongoTemplate.indexOps(Address.class).getIndexInfo();
    List<String> indexNames = indexes.stream().map(IndexInfo::getName).toList();

    // When / Then — metadata pin
    assertThat(indexNames).containsExactlyInAnyOrder("_id_", "org_status_created");

    IndexInfo orgIndex =
        indexes.stream()
            .filter(index -> index.getName().equals("org_status_created"))
            .findFirst()
            .orElseThrow();
    assertThat(orgIndex.getIndexFields())
        .extracting(IndexField::getKey, IndexField::getDirection)
        .containsExactly(
            tuple("organisationId", Sort.Direction.ASC),
            tuple("status", Sort.Direction.ASC),
            tuple("createdAt", Sort.Direction.DESC));

    // When — explain a production-shaped list query
    Document explainCommand =
        new Document(
            "explain",
            new Document("find", "addresses")
                .append(
                    "filter",
                    new Document("organisationId", ORGANISATION_ID)
                        .append("status", AddressStatus.ACTIVE.name()))
                .append("sort", new Document("createdAt", -1))
                .append("limit", DEFAULT_PAGE_SIZE));

    Document explainResult =
        mongoTemplate.execute(
            (com.mongodb.client.MongoDatabase db) -> db.runCommand(explainCommand));

    // Then — planner chooses the compound index
    Document winningPlan = explainResult.get("queryPlanner", Document.class).get("winningPlan", Document.class);
    assertThat(winningPlan.toJson()).contains("org_status_created");
    assertThat(winningPlan.toJson()).doesNotContain("COLLSCAN");
  }
}
