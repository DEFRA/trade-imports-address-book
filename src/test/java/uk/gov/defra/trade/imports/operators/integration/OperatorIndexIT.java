package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import uk.gov.defra.trade.imports.operators.operator.Operator;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Pins the two compound indexes on the {@code operators} collection. A lost index is a silent
 * production incident that no functional test catches, so it is asserted directly against
 * {@code listIndexes}.
 */
class OperatorIndexIT extends IntegrationBase {

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private OperatorRepository operatorRepository;

  @Test
  void operatorsCollection_hasBothCompoundIndexes() {
    operatorRepository.deleteAll();
    operatorRepository.save(Operator.builder().build());

    List<String> indexNames =
        mongoTemplate.indexOps("operators").getIndexInfo().stream().map(IndexInfo::getName).toList();

    assertThat(indexNames).contains("crn_status_type_created", "org_status");
  }
}
