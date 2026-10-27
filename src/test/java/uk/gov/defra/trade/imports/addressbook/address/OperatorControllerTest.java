package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorTestSupport.DEFAULT_PAGE_SIZE;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorTestSupport.NO_TTL;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
            repository, operatorMapper, new SimpleMeterRegistry(), NO_TTL, DEFAULT_PAGE_SIZE);
    controller = new OperatorController(service, operatorMapper);
  }

  @Test
  void list_respondsWithTheRequestedPageAndPageSizeOfTheCallersOrganisation() {
    // Given
    when(repository.searchByCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(List.of("FR", "GB")), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 8), 20));

    // When
    OperatorPageResponse response = controller.list(ORG, ORG, 2, 8, null, List.of("FR", "GB"));

    // Then
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.pageSize()).isEqualTo(8);
    assertThat(response.totalPages()).isEqualTo(3);
  }

  @Test
  void list_forAnotherOrganisationsPathIsNotFound() {
    // Given
    String pathOrganisation = "org-other";

    // When / Then
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> controller.list(pathOrganisation, ORG, 1, null, null, null));
  }
}
