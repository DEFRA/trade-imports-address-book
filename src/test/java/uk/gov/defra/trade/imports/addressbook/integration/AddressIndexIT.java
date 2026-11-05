package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.defra.trade.imports.addressbook.address.AddressService.DEFAULT_PAGE_SIZE;

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

class AddressIndexIT extends IntegrationBase {

  @Autowired private MongoTemplate mongoTemplate;

  @Test
  void index_shouldDeclareOnlyOrgStatusCreatedOnOrganisationStatusAndCreatedAt() {
    List<IndexInfo> indexes = mongoTemplate.indexOps(Address.class).getIndexInfo();

    assertThat(indexes.stream().map(IndexInfo::getName).toList())
        .containsExactlyInAnyOrder("_id_", "org_status_created");
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
  }

  @Test
  void listQuery_shouldUseOrgStatusCreatedIndex() {
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

    Document winningPlan =
        explainResult.get("queryPlanner", Document.class).get("winningPlan", Document.class);
    assertThat(winningPlan.toJson()).contains("org_status_created");
    assertThat(winningPlan.toJson()).doesNotContain("COLLSCAN");
  }
}
