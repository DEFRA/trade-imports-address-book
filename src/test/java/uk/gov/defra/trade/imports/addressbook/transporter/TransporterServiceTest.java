package uk.gov.defra.trade.imports.addressbook.transporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.defra.trade.imports.addressbook.PageTestSupport.pageOf;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.STORED_TRANSPORTER_ID;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.storedTransporter;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.validCommercialRequest;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

@ExtendWith(MockitoExtension.class)
class TransporterServiceTest {

  private static final String ORG = "org-uuid-1";
  private static final AddressTtlConfig NO_TTL = new AddressTtlConfig(null, "dev");
  private static final RequestedTransporterListFilters NO_FILTERS =
      new RequestedTransporterListFilters(null, null, null, null);

  @Mock private TransporterRepository repository;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TransporterMapper transporterMapper = Mappers.getMapper(TransporterMapper.class);

  private TransporterService service;

  @BeforeEach
  void setUp() {
    service = new TransporterService(repository, transporterMapper, meterRegistry, NO_TTL);
  }

  @Test
  void create_setsTheGivenOrganisationIdAndActiveStatus() {
    when(repository.save(any(Transporter.class)))
        .thenAnswer(
            invocation -> {
              Transporter saved = invocation.getArgument(0);
              saved.setId(STORED_TRANSPORTER_ID);
              return saved;
            });

    Transporter created = service.create(validCommercialRequest().build(), ORG);

    assertThat(created.getOrganisationId()).isEqualTo(ORG);
    assertThat(created.getStatus()).isEqualTo(TransporterStatus.ACTIVE);
    assertThat(created.getType()).isEqualTo(TransporterType.COMMERCIAL);
  }

  @Test
  void create_neverSetsServerFieldsFromTheRequest() {
    ArgumentCaptor<Transporter> captor = ArgumentCaptor.forClass(Transporter.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(validCommercialRequest().build(), ORG);

    Transporter persisted = captor.getValue();
    assertThat(persisted.getId()).isNull();
    assertThat(persisted.getCreatedAt()).isNull();
    assertThat(persisted.getModifiedAt()).isNull();
  }

  @Test
  void get_returnsTheTransporterForTheOwningOrganisation() {
    Transporter stored = storedTransporter(ORG, TransporterStatus.ACTIVE).build();
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, ORG))
        .thenReturn(Optional.of(stored));

    assertThat(service.get(STORED_TRANSPORTER_ID, ORG)).contains(stored);
  }

  @Test
  void get_forAnotherOrganisationsIdReturnsEmpty() {
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, "org-other"))
        .thenReturn(Optional.empty());

    assertThat(service.get(STORED_TRANSPORTER_ID, "org-other")).isEmpty();
  }

  @Test
  void update_ofADeletedTransporterThrowsNotFound() {
    Transporter tombstone = storedTransporter(ORG, TransporterStatus.DELETED).build();
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, ORG))
        .thenReturn(Optional.of(tombstone));
    TransporterRequest request = validCommercialRequest().build();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(STORED_TRANSPORTER_ID, request, ORG));
  }

  @Test
  void update_ofAnotherOrganisationsIdThrowsNotFound() {
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, "org-other"))
        .thenReturn(Optional.empty());
    TransporterRequest request = validCommercialRequest().build();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(STORED_TRANSPORTER_ID, request, "org-other"));
  }

  @Test
  void delete_flipsAnActiveTransporterToADeletedTombstone() {
    Transporter existing = storedTransporter(ORG, TransporterStatus.ACTIVE).build();
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Transporter> captor = ArgumentCaptor.forClass(Transporter.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.delete(STORED_TRANSPORTER_ID, ORG);

    assertThat(captor.getValue().getStatus()).isEqualTo(TransporterStatus.DELETED);
  }

  @Test
  void delete_isIdempotentForAnAlreadyDeletedTombstone() {
    Transporter tombstone = storedTransporter(ORG, TransporterStatus.DELETED).build();
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, ORG))
        .thenReturn(Optional.of(tombstone));

    service.delete(STORED_TRANSPORTER_ID, ORG);

    verify(repository, never()).save(any());
  }

  @Test
  void delete_ofAnUnknownOrAnotherOrganisationsIdThrowsNotFound() {
    when(repository.findByIdAndOrganisationId(STORED_TRANSPORTER_ID, "org-other"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(STORED_TRANSPORTER_ID, "org-other"));
  }

  @Test
  void list_withAPageBelow1IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 0, null, NO_FILTERS));
  }

  @Test
  void list_withARequestedPageSizeOutsideBoundsIsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 1, 0, NO_FILTERS));
  }

  @Test
  void list_asksForNewestFirstAndTranslatesToA0BasedPage() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.listLiveTransporters(
            eq(ORG), any(TransporterListCriteria.class), pageableCaptor.capture()))
        .thenReturn(pageOf(List.of(), 1, 25, 0));

    service.list(ORG, 2, null, NO_FILTERS);

    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageableCaptor.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
  }

  private TransporterListCriteria criteriaPassedFor(RequestedTransporterListFilters filters) {
    ArgumentCaptor<TransporterListCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(TransporterListCriteria.class);
    when(repository.listLiveTransporters(eq(ORG), criteriaCaptor.capture(), any(Pageable.class)))
        .thenReturn(pageOf(List.of(), 0, 25, 0));

    service.list(ORG, 1, null, filters);

    return criteriaCaptor.getValue();
  }

  @Test
  void list_passesTheSearchTermAsAPartialMatchPattern() {
    TransporterListCriteria passed =
        criteriaPassedFor(new RequestedTransporterListFilters("UK/BELF", null, null, null));

    assertThat(passed)
        .isEqualTo(
            new TransporterListCriteria(
                List.of(), List.of(new TransporterListCriteria.TextMatching(".*\\QUK/BELF\\E.*"))));
  }

  @Test
  void list_withSeveralCountryCodesTrimsEachAndDropsBlanksAndNulls() {
    TransporterListCriteria passed =
        criteriaPassedFor(
            new RequestedTransporterListFilters(
                null, Arrays.asList(" GB-NIR ", " ", null, "ES"), null, null));

    assertThat(passed)
        .isEqualTo(
            new TransporterListCriteria(
                List.of(),
                List.of(new TransporterListCriteria.InCountries(List.of("GB-NIR", "ES")))));
  }

  @Test
  void list_passesTheTypesToKeepWithoutNullsOrRepeats() {
    TransporterListCriteria passed =
        criteriaPassedFor(
            new RequestedTransporterListFilters(
                null,
                null,
                Arrays.asList(TransporterType.COMMERCIAL, null, TransporterType.COMMERCIAL),
                null));

    assertThat(passed)
        .isEqualTo(new TransporterListCriteria(List.of(TransporterType.COMMERCIAL), List.of()));
  }

  @Test
  void list_passesTheSearchTypesAsOneMatch() {
    TransporterListCriteria passed =
        criteriaPassedFor(
            new RequestedTransporterListFilters(
                null, null, null, List.of(TransporterType.PRIVATE, TransporterType.COMMERCIAL)));

    assertThat(passed)
        .isEqualTo(
            new TransporterListCriteria(
                List.of(),
                List.of(
                    new TransporterListCriteria.OfType(
                        List.of(TransporterType.PRIVATE, TransporterType.COMMERCIAL)))));
  }
}
