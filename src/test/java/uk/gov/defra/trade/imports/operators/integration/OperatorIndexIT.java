package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import uk.gov.defra.trade.imports.operators.operator.Address;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Pins the single compound index on the addresses collection. A lost index is a silent production
 * incident that no functional test catches, so it is asserted directly against {@code listIndexes}.
 * Re-keyed org-for-crn: the old {@code crn_status_type_created} index is gone and the org-scoped,
 * status-bounded, newest-first {@code org_status_created} index serves every production read.
 */
class OperatorIndexIT extends IntegrationBase {

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private OperatorRepository operatorRepository;

  @Test
  void addressesCollection_hasTheOrgStatusCreatedCompoundIndex() {
    operatorRepository.deleteAll();
    operatorRepository.save(Address.builder().build());

    List<IndexInfo> indexes = mongoTemplate.indexOps("operators").getIndexInfo();
    List<String> indexNames = indexes.stream().map(IndexInfo::getName).toList();

    assertThat(indexNames).contains("org_status_created");
    assertThat(indexNames).doesNotContain("crn_status_type_created", "org_status");

    IndexInfo orgIndex =
        indexes.stream()
            .filter(index -> index.getName().equals("org_status_created"))
            .findFirst()
            .orElseThrow();
    assertThat(orgIndex.getIndexFields())
        .extracting(field -> field.getKey())
        .containsExactly("organisationId", "status", "createdAt");
  }
}
