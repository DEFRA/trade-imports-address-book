package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.operators.operator.Operator;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;
import uk.gov.defra.trade.imports.operators.operator.OperatorStatus;
import uk.gov.defra.trade.imports.operators.operator.OperatorType;

/**
 * Proves {@link OperatorRepository#findByIdAndCrn} scopes reads by CRN, so one organisation's CRN
 * can never fetch another organisation's operator by guessing its id.
 */
class OperatorRepositoryIT extends IntegrationBase {

  @Autowired
  private OperatorRepository operatorRepository;

  @BeforeEach
  void clear() {
    operatorRepository.deleteAll();
  }

  @Test
  void findByIdAndCrn_returnsOperator_whenCrnMatches() {
    Operator saved =
        operatorRepository.save(
            Operator.builder()
                .operatorType(OperatorType.CONSIGNOR)
                .name("Acme Farms")
                .addressLine1("1 Farm Lane")
                .town("Exeter")
                .postcode("EX1 1AA")
                .country("United Kingdom")
                .telephone("01234567890")
                .email("acme@example.com")
                .crn("CRN-A")
                .organisationId("ORG-A")
                .status(OperatorStatus.ACTIVE)
                .build());

    Optional<Operator> found = operatorRepository.findByIdAndCrn(saved.getId(), "CRN-A");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Acme Farms");
    assertThat(found.get().getCountry()).isEqualTo("United Kingdom");
  }

  @Test
  void findByIdAndCrn_returnsEmpty_whenCrnDiffers() {
    Operator saved =
        operatorRepository.save(
            Operator.builder()
                .operatorType(OperatorType.IMPORTER)
                .name("Other Traders")
                .addressLine1("2 Trade Road")
                .town("Bristol")
                .postcode("BS1 2BB")
                .country("United Kingdom")
                .telephone("09876543210")
                .email("other@example.com")
                .crn("CRN-A")
                .organisationId("ORG-A")
                .status(OperatorStatus.ACTIVE)
                .build());

    Optional<Operator> found = operatorRepository.findByIdAndCrn(saved.getId(), "CRN-B");

    assertThat(found).isEmpty();
  }
}
