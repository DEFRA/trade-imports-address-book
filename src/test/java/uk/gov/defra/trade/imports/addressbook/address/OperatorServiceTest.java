package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.operators.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.operators.exceptions.NotFoundException;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

  private static final String ORG = "org-uuid-1";

  @Mock private OperatorRepository repository;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private OperatorService service;

  @BeforeEach
  void setUp() {
    service = new OperatorService(repository, meterRegistry, 25);
  }

  private AddressRequest request() {
    return AddressRequest.builder()
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .addressLine2("Unit 3")
        .townOrCity("Inverness")
        .county("Highland")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .build();
  }

  @Test
  void createStampsOrganisationIdAndActiveStatusFromTheHeader() {
    when(repository.save(any(Address.class)))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setId("665f1c2ab3e4d51a2c9d0e77");
              saved.setCreatedAt(Instant.parse("2026-07-14T09:15:27Z"));
              saved.setModifiedAt(Instant.parse("2026-07-14T09:15:27Z"));
              return saved;
            });

    Address created = service.create(request(), ORG);

    assertThat(created.getOrganisationId()).isEqualTo(ORG);
    assertThat(created.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(created.getName()).isEqualTo("Highland Livestock Ltd");
    assertThat(created.getCountryCode()).isEqualTo("GB");
    assertThat(created.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
  }

  @Test
  void createNeverSetsServerFieldsFromTheRequestAndLeavesIdForMongo() {
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(request(), ORG);

    Address persisted = captor.getValue();
    assertThat(persisted.getId()).isNull();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getCreatedAt()).isNull();
    assertThat(persisted.getModifiedAt()).isNull();
  }

  private Address persistedAddress(String id, String organisationId, AddressStatus status) {
    return Address.builder()
        .id(id)
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .townOrCity("Inverness")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .organisationId(organisationId)
        .status(status)
        .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
        .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
        .build();
  }

  @Test
  void getReturnsTheAddressForTheOwningOrganisation() {
    Address stored = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(stored));

    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", ORG);

    assertThat(found).contains(stored);
  }

  @Test
  void getForADifferentOrganisationReturnsEmptySoTheControllerCan404() {
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", "org-other");

    assertThat(found).isEmpty();
  }

  @Test
  void getOfADeletedAddressReturnsItWithStatusDeletedBecauseATombstoneIsFetchable() {
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", ORG);

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(AddressStatus.DELETED);
  }

  private AddressRequest updateRequest() {
    return AddressRequest.builder()
        .name("Lowland Cattle Co")
        .addressLine1("2 Market Street")
        .addressLine2("Suite 5")
        .townOrCity("Perth")
        .county("Perth and Kinross")
        .postcode("PH1 5AA")
        .countryCode("GB")
        .phone("+44 1738 111222")
        .email("ops@lowlandcattle.example.com")
        .build();
  }

  @Test
  void updateAppliesTheNewFieldValuesBumpsModifiedAtAndPreservesTheServerOwnedFields() {
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    Instant bumped = Instant.parse("2026-07-15T10:00:00Z");
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture()))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setModifiedAt(bumped);
              return saved;
            });

    Address updated = service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG);

    assertThat(updated.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(updated.getAddressLine1()).isEqualTo("2 Market Street");
    assertThat(updated.getTownOrCity()).isEqualTo("Perth");
    assertThat(updated.getPostcode()).isEqualTo("PH1 5AA");
    assertThat(updated.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
    assertThat(updated.getModifiedAt()).isEqualTo(bumped);
    // server-owned fields are untouched by the wire
    assertThat(updated.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(updated.getOrganisationId()).isEqualTo(ORG);
    assertThat(updated.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(updated.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));

    Address persisted = captor.getValue();
    assertThat(persisted.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));
  }

  @Test
  void updateOfADeletedTombstoneIs404BecauseItIsOutsideTheCallersLiveSet() {
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG));
  }

  @Test
  void updateOfACrossOrgIdIs404BecauseTheStoreReturnsEmpty() {
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), "org-other"));
  }

  @Test
  void deleteFlipsAnActiveAddressToAdeletedTombstoneAndBumpsModifiedAt() {
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    Instant bumped = Instant.parse("2026-07-15T10:00:00Z");
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture()))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setModifiedAt(bumped);
              return saved;
            });

    service.delete("665f1c2ab3e4d51a2c9d0e77", ORG);

    Address persisted = captor.getValue();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(persisted.getModifiedAt()).isEqualTo(bumped);
  }

  @Test
  void deleteOfAnAlreadyDeletedTombstoneIsIdempotentAndLeavesTheTombstoneUntouched() {
    Instant originalModifiedAt = Instant.parse("2026-07-14T09:15:27Z");
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    service.delete("665f1c2ab3e4d51a2c9d0e77", ORG);

    assertThat(tombstone.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(tombstone.getModifiedAt()).isEqualTo(originalModifiedAt);
  }

  @Test
  void deleteOfACrossOrgOrUnknownIdIs404BecauseTheStoreReturnsEmpty() {
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete("665f1c2ab3e4d51a2c9d0e77", "org-other"));
  }

  private List<Address> activeAddresses(int count) {
    List<Address> addresses = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      addresses.add(persistedAddress("665f1c2ab3e4d51a2c9d0e" + i, ORG, AddressStatus.ACTIVE));
    }
    return addresses;
  }

  @Test
  void listReturnsAFullFirstPageOf25WithTotalPages2For30ActiveAddresses() {
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeAddresses(25), PageRequest.of(0, 25), 30));

    OperatorPageResponse response = service.list(ORG, 1);

    assertThat(response.items()).hasSize(25);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(25);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void listPageTwoReturnsTheRemaining5AddressesWithTotalPagesStill2() {
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeAddresses(5), PageRequest.of(1, 25), 30));

    OperatorPageResponse response = service.list(ORG, 2);

    assertThat(response.items()).hasSize(5);
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void listScopesTheQueryToTheActiveOrgNewestFirstAndTranslatesToA0BasedPage() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 25), 0));

    service.list(ORG, 2);

    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(25);
    assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void listUsesTheConfiguredPageSizeNotAValuePassedByTheCaller() {
    OperatorService configured = new OperatorService(repository, meterRegistry, 10);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    OperatorPageResponse response = configured.list(ORG, 1);

    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    assertThat(response.pageSize()).isEqualTo(10);
  }

  @Test
  void listWithAPageBelow1IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 0));
  }
}
