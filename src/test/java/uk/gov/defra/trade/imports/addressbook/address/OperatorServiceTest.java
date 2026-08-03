package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

  private static final String ORG = "org-uuid-1";

  @Mock private OperatorRepository repository;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final OperatorMapper operatorMapper = Mappers.getMapper(OperatorMapper.class);

  private OperatorService service;

  @BeforeEach
  void setUp() {
    service = new OperatorService(repository, operatorMapper, meterRegistry, 25);
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
  void create_stampsOrganisationIdAndActiveStatusFromTheHeader() {
    // Given
    when(repository.save(any(Address.class)))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setId("665f1c2ab3e4d51a2c9d0e77");
              saved.setCreatedAt(Instant.parse("2026-07-14T09:15:27Z"));
              saved.setModifiedAt(Instant.parse("2026-07-14T09:15:27Z"));
              return saved;
            });

    // When
    Address created = service.create(request(), ORG);

    // Then
    assertThat(created.getOrganisationId()).isEqualTo(ORG);
    assertThat(created.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(created.getName()).isEqualTo("Highland Livestock Ltd");
    assertThat(created.getCountryCode()).isEqualTo("GB");
    assertThat(created.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
  }

  @Test
  void create_neverSetsServerFieldsFromTheRequestAndLeavesIdForMongo() {
    // Given
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    service.create(request(), ORG);

    // Then
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
  void get_returnsTheAddressForTheOwningOrganisation() {
    // Given
    Address stored = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(stored));

    // When
    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", ORG);

    // Then
    assertThat(found).contains(stored);
  }

  @Test
  void get_forADifferentOrganisationReturnsEmptySoTheControllerCan404() {
    // Given
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    // When
    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", "org-other");

    // Then
    assertThat(found).isEmpty();
  }

  @Test
  void get_ofADeletedAddressReturnsItWithStatusDeletedBecauseATombstoneIsFetchable() {
    // Given
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    // When
    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", ORG);

    // Then
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
  void update_appliesAllRequestFieldValuesAndPreservesTheServerOwnedFields() {
    // Given
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Address updated = service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG);

    // Then
    assertThat(updated.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(updated.getAddressLine1()).isEqualTo("2 Market Street");
    assertThat(updated.getAddressLine2()).isEqualTo("Suite 5");
    assertThat(updated.getTownOrCity()).isEqualTo("Perth");
    assertThat(updated.getCounty()).isEqualTo("Perth and Kinross");
    assertThat(updated.getPostcode()).isEqualTo("PH1 5AA");
    assertThat(updated.getCountryCode()).isEqualTo("GB");
    assertThat(updated.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(updated.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
    assertThat(updated.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(updated.getOrganisationId()).isEqualTo(ORG);
    assertThat(updated.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(updated.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));

    Address persisted = captor.getValue();
    assertThat(persisted.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(persisted.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));
  }

  @Test
  void update_ofADeletedTombstoneIs404BecauseItIsOutsideTheCallersLiveSet() {
    // Given
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    // When & Then
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG));
  }

  @Test
  void update_ofACrossOrgIdIs404BecauseTheStoreReturnsEmpty() {
    // Given
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    // When & Then
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), "org-other"));
  }

  @Test
  void delete_flipsAnActiveAddressToADeletedTombstone() {
    // Given
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    service.delete("665f1c2ab3e4d51a2c9d0e77", ORG);

    // Then
    Address persisted = captor.getValue();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
  }

  @Test
  void delete_isIdempotentForAnAlreadyDeletedTombstone() {
    // Given
    Instant originalModifiedAt = Instant.parse("2026-07-14T09:15:27Z");
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    // When
    service.delete("665f1c2ab3e4d51a2c9d0e77", ORG);

    // Then
    verify(repository, never()).save(any());
    assertThat(tombstone.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(tombstone.getModifiedAt()).isEqualTo(originalModifiedAt);
  }

  @Test
  void delete_ofACrossOrgOrUnknownIdIs404BecauseTheStoreReturnsEmpty() {
    // Given
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    // When & Then
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
  void list_returnsAFullFirstPageOf25WithTotalPages2For30ActiveAddresses() {
    // Given
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeAddresses(25), PageRequest.of(0, 25), 30));

    // When
    OperatorPageResponse response = service.list(ORG, 1, null, null);

    // Then
    assertThat(response.items()).hasSize(25);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(25);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void list_pageTwoReturnsTheRemaining5AddressesWithTotalPagesStill2() {
    // Given
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeAddresses(5), PageRequest.of(1, 25), 30));

    // When
    OperatorPageResponse response = service.list(ORG, 2, null, null);

    // Then
    assertThat(response.items()).hasSize(5);
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void list_scopesTheQueryToTheActiveOrgNewestFirstAndTranslatesToA0BasedPage() {
    // Given
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 25), 0));

    // When
    service.list(ORG, 2, null, null);

    // Then
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(25);
    assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void list_usesTheConfiguredPageSizeNotAValuePassedByTheCaller() {
    // Given
    OperatorService configured = new OperatorService(repository, operatorMapper, meterRegistry, 10);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    // When
    OperatorPageResponse response = configured.list(ORG, 1, null, null);

    // Then
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    assertThat(response.pageSize()).isEqualTo(10);
  }

  @Test
  void list_withAPageBelow1IsABadRequest() {
    // When & Then
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 0, null, null));
  }

  @Test
  void list_withQueryUsesTheSearchRepository() {
    // Given
    when(repository.searchByQuery(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\Qfarm\\E.*"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    OperatorPageResponse response = service.list(ORG, 1, "farm", null);

    // Then
    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_withWhitespacePaddedQueryTrimsBeforeSearching() {
    // Given
    when(repository.searchByQuery(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\Qfarm\\E.*"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    service.list(ORG, 1, "  farm  ", null);

    // Then — trim is applied before regex construction
    verify(repository)
        .searchByQuery(eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\Qfarm\\E.*"), any(Pageable.class));
  }

  @Test
  void list_withBlankQueryFallsBackToUnfilteredListing() {
    // Given
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    OperatorPageResponse response = service.list(ORG, 1, "   ", null);

    // Then
    assertThat(response.totalItems()).isZero();
    verify(repository).findByOrganisationIdAndStatus(eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class));
  }

  @Test
  void list_withCountryCodeUsesTheCountrySearchRepository() {
    // Given
    when(repository.searchByCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq("FR"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    OperatorPageResponse response = service.list(ORG, 1, null, "FR");

    // Then
    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_withWhitespacePaddedCountryCodeTrimsBeforeSearching() {
    // Given
    when(repository.searchByCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq("FR"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    service.list(ORG, 1, null, " FR ");

    // Then
    verify(repository)
        .searchByCountryCode(eq(ORG), eq(AddressStatus.ACTIVE), eq("FR"), any(Pageable.class));
  }

  @Test
  void list_withBlankCountryCodeFallsBackToUnfilteredListing() {
    // Given
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    OperatorPageResponse response = service.list(ORG, 1, null, "  ");

    // Then
    assertThat(response.totalItems()).isZero();
    verify(repository).findByOrganisationIdAndStatus(eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class));
  }

  @Test
  void list_withQueryAndCountryCodeUsesTheCombinedSearchRepository() {
    // Given
    when(repository.searchByQueryAndCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\QFrance\\E.*"), eq("FR"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    // When
    OperatorPageResponse response = service.list(ORG, 1, "France", "FR");

    // Then
    assertThat(response.totalItems()).isZero();
  }
}
