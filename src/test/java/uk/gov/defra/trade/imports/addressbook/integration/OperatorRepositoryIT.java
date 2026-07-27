package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.operators.operator.Address;
import uk.gov.defra.trade.imports.operators.operator.AddressStatus;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Proves {@link OperatorRepository#findByIdAndOrganisationId} scopes reads by organisation, so one
 * organisation can never fetch another organisation's address by guessing its id (cv-010).
 */
class OperatorRepositoryIT extends IntegrationBase {

  @Autowired
  private OperatorRepository operatorRepository;

  @BeforeEach
  void clear() {
    operatorRepository.deleteAll();
  }

  @Test
  void findByIdAndOrganisationId_returnsAddress_whenOrganisationMatches() {
    Address saved =
        operatorRepository.save(
            Address.builder()
                .name("Acme Farms")
                .addressLine1("1 Farm Lane")
                .townOrCity("Exeter")
                .postcode("EX1 1AA")
                .countryCode("GB")
                .phone("01234567890")
                .email("acme@example.com")
                .organisationId("ORG-A")
                .status(AddressStatus.ACTIVE)
                .build());

    Optional<Address> found = operatorRepository.findByIdAndOrganisationId(saved.getId(), "ORG-A");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Acme Farms");
    assertThat(found.get().getCountryCode()).isEqualTo("GB");
  }

  @Test
  void findByIdAndOrganisationId_returnsEmpty_whenOrganisationDiffers() {
    Address saved =
        operatorRepository.save(
            Address.builder()
                .name("Other Traders")
                .addressLine1("2 Trade Road")
                .townOrCity("Bristol")
                .postcode("BS1 2BB")
                .countryCode("GB")
                .phone("09876543210")
                .email("other@example.com")
                .organisationId("ORG-A")
                .status(AddressStatus.ACTIVE)
                .build());

    Optional<Address> found = operatorRepository.findByIdAndOrganisationId(saved.getId(), "ORG-B");

    assertThat(found).isEmpty();
  }
}
