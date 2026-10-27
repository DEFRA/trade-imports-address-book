package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

@ExtendWith(MockitoExtension.class)
class OperatorControllerTest {

  private static final String ORG = "org-uuid-1";

  @Mock private OperatorRepository repository;

  private OperatorController controller;

  @BeforeEach
  void setUp() {
    OperatorMapper operatorMapper = Mappers.getMapper(OperatorMapper.class);
    OperatorService service =
        new OperatorService(
            repository,
            operatorMapper,
            new SimpleMeterRegistry(),
            new AddressTtlConfig(null, "dev"),
            25);
    controller = new OperatorController(service, operatorMapper);
  }

  @Test
  void list_servesTheRequestedPageSizeOfTheCallersOrganisation() {
    // Given
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 8), 20));

    // When
    OperatorPageResponse response = controller.list(ORG, ORG, 2, 8, null, null);

    // Then
    assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(8);
    assertThat(response.totalPages()).isEqualTo(3);
  }

  @Test
  void list_searchesEveryRequestedCountryCode() {
    // Given
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> countryCodes = ArgumentCaptor.forClass(List.class);
    when(repository.searchByCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), countryCodes.capture(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    controller.list(ORG, ORG, 1, null, null, List.of("FR", "GB"));

    // Then
    assertThat(countryCodes.getValue()).containsExactly("FR", "GB");
  }

  @Test
  void list_forAnotherOrganisationsPathIsNotFound() {
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> controller.list("org-other", ORG, 1, null, null, null));
  }
}
