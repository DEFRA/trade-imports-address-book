package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorService.DEFAULT_PAGE_SIZE;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorTestSupport.NO_TTL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.addressbook.metrics.AddressMetrics;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

  private static final String ORG = "org-uuid-1";
  private static final String OTHER_ORG = "org-other";
  private static final String ADDRESS_ID = "665f1c2ab3e4d51a2c9d0e77";

  @Mock private OperatorRepository repository;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final OperatorMapper operatorMapper = Mappers.getMapper(OperatorMapper.class);
  private final AddressMetrics addressMetrics = new AddressMetrics(meterRegistry);

  private OperatorService service;

  @BeforeEach
  void setUp() {
    service = new OperatorService(repository, operatorMapper, NO_TTL, addressMetrics);
  }

  private OperatorService serviceWithTtl(Integer days, String environment) {
    return new OperatorService(
        repository, operatorMapper, new AddressTtlConfig(days, environment), addressMetrics);
  }

  private void givenStored(Address address) {
    when(repository.findByIdAndOrganisationId(address.getId(), address.getOrganisationId()))
        .thenReturn(Optional.of(address));
  }

  private void givenNothingStoredFor(String organisationId) {
    when(repository.findByIdAndOrganisationId(ADDRESS_ID, organisationId))
        .thenReturn(Optional.empty());
  }

  private ArgumentCaptor<Address> givenSaveReturnsWhatItSaves() {
    ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
    when(repository.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));
    return saved;
  }

  private List<String> countedChanges() {
    return countedTagValues("AddressChange", "Action");
  }

  private List<String> countedSearches() {
    return countedTagValues("AddressSearch", "SearchType");
  }

  private List<String> countedTagValues(String counterName, String tagKey) {
    return meterRegistry.find(counterName).counters().stream()
        .flatMap(
            counter ->
                Collections.nCopies((int) counter.count(), counter.getId().getTag(tagKey))
                    .stream())
        .toList();
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
    when(repository.save(any(Address.class)))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setId(ADDRESS_ID);
              saved.setCreatedAt(Instant.parse("2026-07-14T09:15:27Z"));
              saved.setModifiedAt(Instant.parse("2026-07-14T09:15:27Z"));
              return saved;
            });

    Address created = service.create(request(), ORG);

    assertThat(created.getOrganisationId()).isEqualTo(ORG);
    assertThat(created.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(created.getName()).isEqualTo("Highland Livestock Ltd");
    assertThat(created.getCountryCode()).isEqualTo("GB");
    assertThat(created.getId()).isEqualTo(ADDRESS_ID);
  }

  @Test
  void create_countsOneCreateChange() {
    givenSaveReturnsWhatItSaves();

    service.create(request(), ORG);

    assertThat(countedChanges()).containsExactly("create");
  }

  @Test
  void create_neverSetsServerFieldsFromTheRequestAndLeavesIdForMongo() {
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    service.create(request(), ORG);

    Address persisted = saved.getValue();
    assertThat(persisted.getId()).isNull();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getCreatedAt()).isNull();
    assertThat(persisted.getModifiedAt()).isNull();
  }

  @Test
  void create_inANonProdEnvironmentWithTtlDaysSetsExpireAtThatManyDaysAhead() {
    OperatorService withTtl = serviceWithTtl(7, "dev");
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();
    Instant before = Instant.now();

    withTtl.create(request(), ORG);

    Instant after = Instant.now();
    assertThat(saved.getValue().getExpireAt())
        .isBetween(before.plus(7, ChronoUnit.DAYS), after.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void create_withNoTtlDaysConfiguredLeavesExpireAtUnset() {
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    service.create(request(), ORG);

    assertThat(saved.getValue().getExpireAt()).isNull();
  }

  @Test
  void create_inProdNeverSetsExpireAtEvenWithTtlDaysConfigured() {
    OperatorService inProd = serviceWithTtl(7, "prod");
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    inProd.create(request(), ORG);

    assertThat(saved.getValue().getExpireAt()).isNull();
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

  private Address storedAddress(AddressStatus status) {
    return persistedAddress(ADDRESS_ID, ORG, status);
  }

  @Test
  void get_returnsTheAddressForTheOwningOrganisation() {
    Address stored = storedAddress(AddressStatus.ACTIVE);
    givenStored(stored);

    Optional<Address> found = service.get(ADDRESS_ID, ORG);

    assertThat(found).contains(stored);
  }

  @Test
  void get_forAnotherOrganisationsIdReturnsEmpty() {
    givenNothingStoredFor(OTHER_ORG);

    Optional<Address> found = service.get(ADDRESS_ID, OTHER_ORG);

    assertThat(found).isEmpty();
  }

  @Test
  void get_ofADeletedAddressReturnsItWithStatusDeleted() {
    givenStored(storedAddress(AddressStatus.DELETED));

    Optional<Address> found = service.get(ADDRESS_ID, ORG);

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
    givenStored(storedAddress(AddressStatus.ACTIVE));
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    Address updated = service.update(ADDRESS_ID, updateRequest(), ORG);

    assertThat(updated.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(updated.getAddressLine1()).isEqualTo("2 Market Street");
    assertThat(updated.getAddressLine2()).isEqualTo("Suite 5");
    assertThat(updated.getTownOrCity()).isEqualTo("Perth");
    assertThat(updated.getCounty()).isEqualTo("Perth and Kinross");
    assertThat(updated.getPostcode()).isEqualTo("PH1 5AA");
    assertThat(updated.getCountryCode()).isEqualTo("GB");
    assertThat(updated.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(updated.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
    assertThat(updated.getId()).isEqualTo(ADDRESS_ID);
    assertThat(updated.getOrganisationId()).isEqualTo(ORG);
    assertThat(updated.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(updated.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));

    Address persisted = saved.getValue();
    assertThat(persisted.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(persisted.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(persisted.getId()).isEqualTo(ADDRESS_ID);
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));
  }

  @Test
  void update_countsOneUpdateChange() {
    givenStored(storedAddress(AddressStatus.ACTIVE));
    givenSaveReturnsWhatItSaves();

    service.update(ADDRESS_ID, updateRequest(), ORG);

    assertThat(countedChanges()).containsExactly("update");
  }

  @Test
  void update_keepsTheExpireAtSetWhenTheAddressWasCreated() {
    OperatorService withTtl = serviceWithTtl(7, "dev");
    Address existing = storedAddress(AddressStatus.ACTIVE);
    existing.setExpireAt(Instant.parse("2026-07-21T09:15:27Z"));
    givenStored(existing);
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    withTtl.update(ADDRESS_ID, updateRequest(), ORG);

    assertThat(saved.getValue().getExpireAt()).isEqualTo(Instant.parse("2026-07-21T09:15:27Z"));
  }

  @Test
  void update_ofAnAddressWithNoExpireAtLeavesItUnsetEvenWithTtlDaysConfigured() {
    OperatorService withTtl = serviceWithTtl(7, "dev");
    givenStored(storedAddress(AddressStatus.ACTIVE));
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    withTtl.update(ADDRESS_ID, updateRequest(), ORG);

    assertThat(saved.getValue().getExpireAt()).isNull();
  }

  @Test
  void update_ofADeletedAddressThrowsNotFound() {
    givenStored(storedAddress(AddressStatus.DELETED));
    AddressRequest request = updateRequest();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(ADDRESS_ID, request, ORG));
  }

  @Test
  void update_ofAnotherOrganisationsIdThrowsNotFound() {
    givenNothingStoredFor(OTHER_ORG);
    AddressRequest request = updateRequest();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(ADDRESS_ID, request, OTHER_ORG));
  }

  @Test
  void delete_flipsAnActiveAddressToADeletedTombstone() {
    givenStored(storedAddress(AddressStatus.ACTIVE));
    ArgumentCaptor<Address> saved = givenSaveReturnsWhatItSaves();

    service.delete(ADDRESS_ID, ORG);

    Address persisted = saved.getValue();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(persisted.getId()).isEqualTo(ADDRESS_ID);
  }

  @Test
  void delete_countsOneDeleteChange() {
    givenStored(storedAddress(AddressStatus.ACTIVE));
    givenSaveReturnsWhatItSaves();

    service.delete(ADDRESS_ID, ORG);

    assertThat(countedChanges()).containsExactly("delete");
  }

  @Test
  void delete_ofAnAlreadyDeletedTombstoneCountsNoChange() {
    givenStored(storedAddress(AddressStatus.DELETED));

    service.delete(ADDRESS_ID, ORG);

    assertThat(countedChanges()).isEmpty();
  }

  @Test
  void delete_isIdempotentForAnAlreadyDeletedTombstone() {
    Instant originalModifiedAt = Instant.parse("2026-07-14T09:15:27Z");
    Address tombstone = storedAddress(AddressStatus.DELETED);
    givenStored(tombstone);

    service.delete(ADDRESS_ID, ORG);

    verify(repository, never()).save(any());
    assertThat(tombstone.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(tombstone.getModifiedAt()).isEqualTo(originalModifiedAt);
  }

  @Test
  void delete_ofAnUnknownOrAnotherOrganisationsIdThrowsNotFound() {
    givenNothingStoredFor(OTHER_ORG);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(ADDRESS_ID, OTHER_ORG));
  }

  private List<Address> activeAddresses(int count) {
    List<Address> addresses = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      addresses.add(persistedAddress("665f1c2ab3e4d51a2c9d0e" + i, ORG, AddressStatus.ACTIVE));
    }
    return addresses;
  }

  @Test
  void list_returnsAFullFirstPageWithTotalPages2For30ActiveAddresses() {
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeAddresses(DEFAULT_PAGE_SIZE), PageRequest.of(0, DEFAULT_PAGE_SIZE), 30));

    OperatorPageResponse response = service.list(ORG, 1, null, null, null);

    assertThat(response.items()).hasSize(DEFAULT_PAGE_SIZE);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void list_pageTwoReturnsTheRemaining5AddressesWithTotalPagesStill2() {
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeAddresses(5), PageRequest.of(1, DEFAULT_PAGE_SIZE), 30));

    OperatorPageResponse response = service.list(ORG, 2, null, null, null);

    assertThat(response.items()).hasSize(5);
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void list_scopesTheQueryToTheActiveOrgNewestFirstAndTranslatesToA0BasedPage() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, DEFAULT_PAGE_SIZE), 0));

    service.list(ORG, 2, null, null, null);

    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 8, 100})
  void list_withARequestedPageSizeServesThatPageSize(int requestedPageSize) {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, requestedPageSize), 0));

    OperatorPageResponse response = service.list(ORG, 1, requestedPageSize, null, null);

    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(requestedPageSize);
    assertThat(response.pageSize()).isEqualTo(requestedPageSize);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 101})
  void list_withARequestedPageSizeOutside1To100IsABadRequest(int requestedPageSize) {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 1, requestedPageSize, null, null));
  }

  @Test
  void list_withSeveralCountryCodesTrimsEachAndDropsBlanksAndNullsBeforeSearching() {
    Address matching = persistedAddress("matching-id", ORG, AddressStatus.ACTIVE);
    when(repository.searchByAnyCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(List.of("FR", "GB")), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(matching), PageRequest.of(0, DEFAULT_PAGE_SIZE), 1));

    OperatorPageResponse response = service.list(ORG, 1, null, null, Arrays.asList(" FR ", " ", null, "GB"));

    assertThat(response.items()).extracting(OperatorResponse::id).containsExactly("matching-id");
  }

  @Test
  void list_withAPageBelow1IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 0, null, null, null));
  }

  @Test
  void list_withAPageBelow1CountsNoSearch() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 0, null, "farm", null));

    assertThat(countedSearches()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "null, null, none",
        "'   ', '  ', none",
        "farm, null, query",
        "null, FR, country",
        "France, FR, query-and-country"
      })
  void list_countsOneSearchOfTheTypeItsFiltersMake(
      String q, String countryCode, String searchType) {
    givenEverySearchFindsNothing();
    List<String> countryCodes = countryCode == null ? null : List.of(countryCode);

    service.list(ORG, 1, null, q, countryCodes);

    assertThat(countedSearches()).containsExactly(searchType);
  }

  private void givenEverySearchFindsNothing() {
    PageImpl<Address> empty = new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0);
    lenient()
        .when(repository.findByOrganisationIdAndStatus(any(), any(), any(Pageable.class)))
        .thenReturn(empty);
    lenient()
        .when(repository.searchByQuery(any(), any(), any(), any(Pageable.class)))
        .thenReturn(empty);
    lenient()
        .when(repository.searchByAnyCountryCode(any(), any(), any(), any(Pageable.class)))
        .thenReturn(empty);
    lenient()
        .when(repository.searchByQueryOrAnyCountryCode(any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(empty);
  }

  @Test
  void list_withQueryUsesTheSearchRepository() {
    when(repository.searchByQuery(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\Qfarm\\E.*"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0));

    OperatorPageResponse response = service.list(ORG, 1, null, "farm", null);

    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_withWhitespacePaddedQueryTrimsBeforeSearching() {
    Address matching = persistedAddress("matching-id", ORG, AddressStatus.ACTIVE);
    when(repository.searchByQuery(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\Qfarm\\E.*"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(matching), PageRequest.of(0, DEFAULT_PAGE_SIZE), 1));

    OperatorPageResponse response = service.list(ORG, 1, null, "  farm  ", null);

    assertThat(response.items()).extracting(OperatorResponse::id).containsExactly("matching-id");
  }

  @Test
  void list_withBlankQueryFallsBackToUnfilteredListing() {
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0));

    OperatorPageResponse response = service.list(ORG, 1, null, "   ", null);

    assertThat(response.totalItems()).isZero();
    verify(repository).findByOrganisationIdAndStatus(eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class));
  }

  @Test
  void list_withCountryCodeSearchesByAnyCountryCode() {
    when(repository.searchByAnyCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(List.of("FR")), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0));

    OperatorPageResponse response = service.list(ORG, 1, null, null, List.of("FR"));

    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_withWhitespacePaddedCountryCodeTrimsBeforeSearching() {
    Address matching = persistedAddress("matching-id", ORG, AddressStatus.ACTIVE);
    when(repository.searchByAnyCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(List.of("FR")), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(matching), PageRequest.of(0, DEFAULT_PAGE_SIZE), 1));

    OperatorPageResponse response = service.list(ORG, 1, null, null, List.of(" FR "));

    assertThat(response.items()).extracting(OperatorResponse::id).containsExactly("matching-id");
  }

  @Test
  void list_withBlankCountryCodeFallsBackToUnfilteredListing() {
    when(repository.findByOrganisationIdAndStatus(
            eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0));

    OperatorPageResponse response = service.list(ORG, 1, null, null, List.of("  "));

    assertThat(response.totalItems()).isZero();
    verify(repository).findByOrganisationIdAndStatus(eq(ORG), eq(AddressStatus.ACTIVE), any(Pageable.class));
  }

  @Test
  void list_withQueryAndCountryCodeSearchesByQueryOrAnyCountryCode() {
    when(repository.searchByQueryOrAnyCountryCode(
            eq(ORG), eq(AddressStatus.ACTIVE), eq(".*\\QFrance\\E.*"), eq(List.of("FR")), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0));

    OperatorPageResponse response = service.list(ORG, 1, null, "France", List.of("FR"));

    assertThat(response.totalItems()).isZero();
  }
}
