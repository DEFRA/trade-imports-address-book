package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorService.DEFAULT_PAGE_SIZE;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorTestSupport.NO_LIST_CRITERIA;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorTestSupport.NO_TTL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
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
    service =
        new OperatorService(repository, operatorMapper, meterRegistry, NO_TTL);
  }

  private OperatorService serviceWithTtl(Integer days, String environment) {
    return new OperatorService(
        repository,
        operatorMapper,
        meterRegistry,
        new AddressTtlConfig(days, environment));
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
  void create_neverSetsServerFieldsFromTheRequestAndLeavesIdForMongo() {
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

  @Test
  void create_inANonProdEnvironmentWithTtlDaysSetsExpireAtThatManyDaysAhead() {
    OperatorService withTtl = serviceWithTtl(7, "dev");
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
    Instant before = Instant.now();

    withTtl.create(request(), ORG);

    Instant after = Instant.now();
    assertThat(captor.getValue().getExpireAt())
        .isBetween(before.plus(7, ChronoUnit.DAYS), after.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void create_withNoTtlDaysConfiguredLeavesExpireAtUnset() {
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(request(), ORG);

    assertThat(captor.getValue().getExpireAt()).isNull();
  }

  @Test
  void create_inProdNeverSetsExpireAtEvenWithTtlDaysConfigured() {
    OperatorService inProd = serviceWithTtl(7, "prod");
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    inProd.create(request(), ORG);

    assertThat(captor.getValue().getExpireAt()).isNull();
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
    Address stored = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(stored));

    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", ORG);

    assertThat(found).contains(stored);
  }

  @Test
  void get_forAnotherOrganisationsIdReturnsEmpty() {
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    Optional<Address> found = service.get("665f1c2ab3e4d51a2c9d0e77", "org-other");

    assertThat(found).isEmpty();
  }

  @Test
  void get_ofADeletedAddressReturnsItWithStatusDeleted() {
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
  void update_appliesAllRequestFieldValuesAndPreservesTheServerOwnedFields() {
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    Address updated = service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG);

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
  void update_keepsTheExpireAtSetWhenTheAddressWasCreated() {
    OperatorService withTtl = serviceWithTtl(7, "dev");
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    existing.setExpireAt(Instant.parse("2026-07-21T09:15:27Z"));
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    withTtl.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG);

    assertThat(captor.getValue().getExpireAt()).isEqualTo(Instant.parse("2026-07-21T09:15:27Z"));
  }

  @Test
  void update_ofAnAddressWithNoExpireAtLeavesItUnsetEvenWithTtlDaysConfigured() {
    OperatorService withTtl = serviceWithTtl(7, "dev");
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    withTtl.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG);

    assertThat(captor.getValue().getExpireAt()).isNull();
  }

  @Test
  void update_ofADeletedAddressThrowsNotFound() {
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), ORG));
  }

  @Test
  void update_ofAnotherOrganisationsIdThrowsNotFound() {
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", "org-other"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(), "org-other"));
  }

  @Test
  void delete_flipsAnActiveAddressToADeletedTombstone() {
    Address existing = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.ACTIVE);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.delete("665f1c2ab3e4d51a2c9d0e77", ORG);

    Address persisted = captor.getValue();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
  }

  @Test
  void delete_isIdempotentForAnAlreadyDeletedTombstone() {
    Instant originalModifiedAt = Instant.parse("2026-07-14T09:15:27Z");
    Address tombstone = persistedAddress("665f1c2ab3e4d51a2c9d0e77", ORG, AddressStatus.DELETED);
    when(repository.findByIdAndOrganisationId("665f1c2ab3e4d51a2c9d0e77", ORG))
        .thenReturn(Optional.of(tombstone));

    service.delete("665f1c2ab3e4d51a2c9d0e77", ORG);

    verify(repository, never()).save(any());
    assertThat(tombstone.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(tombstone.getModifiedAt()).isEqualTo(originalModifiedAt);
  }

  @Test
  void delete_ofAnUnknownOrAnotherOrganisationsIdThrowsNotFound() {
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

  private Page<Address> pageOf(List<Address> addresses, int pageIndex, int pageSize, long total) {
    return new PageImpl<>(addresses, PageRequest.of(pageIndex, pageSize), total);
  }

  private Page<Address> emptyFirstPage() {
    return pageOf(List.of(), 0, DEFAULT_PAGE_SIZE, 0);
  }

  private static final RequestedListFilters NO_FILTERS =
      new RequestedListFilters(null, null, null, null, null);

  private static RequestedListFilters filtersWithSearchTerm(String searchTerm) {
    return new RequestedListFilters(searchTerm, null, null, null, null);
  }

  private static RequestedListFilters filtersWithCountryCodes(List<String> countryCodes) {
    return new RequestedListFilters(null, countryCodes, null, null, null);
  }

  @Test
  void list_returnsAFullFirstPageWithTotalPages2For30ActiveAddresses() {
    when(repository.listLiveAddresses(eq(ORG), any(ListCriteria.class), any(Pageable.class)))
        .thenReturn(pageOf(activeAddresses(DEFAULT_PAGE_SIZE), 0, DEFAULT_PAGE_SIZE, 30));

    OperatorPageResponse response = service.list(ORG, 1, null, NO_FILTERS);

    assertThat(response.items()).hasSize(DEFAULT_PAGE_SIZE);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void list_pageTwoReturnsTheRemaining5AddressesWithTotalPagesStill2() {
    when(repository.listLiveAddresses(eq(ORG), any(ListCriteria.class), any(Pageable.class)))
        .thenReturn(pageOf(activeAddresses(5), 1, DEFAULT_PAGE_SIZE, 30));

    OperatorPageResponse response = service.list(ORG, 2, null, NO_FILTERS);

    assertThat(response.items()).hasSize(5);
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void list_asksForNewestFirstAndTranslatesToA0BasedPage() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.listLiveAddresses(eq(ORG), any(ListCriteria.class), pageableCaptor.capture()))
        .thenReturn(pageOf(List.of(), 1, DEFAULT_PAGE_SIZE, 0));

    service.list(ORG, 2, null, NO_FILTERS);

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
    when(repository.listLiveAddresses(eq(ORG), any(ListCriteria.class), pageableCaptor.capture()))
        .thenReturn(pageOf(List.of(), 0, requestedPageSize, 0));

    OperatorPageResponse response = service.list(ORG, 1, requestedPageSize, NO_FILTERS);

    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(requestedPageSize);
    assertThat(response.pageSize()).isEqualTo(requestedPageSize);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 101})
  void list_withARequestedPageSizeOutside1To100IsABadRequest(int requestedPageSize) {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 1, requestedPageSize, NO_FILTERS));
  }

  @Test
  void list_withAPageBelow1IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list(ORG, 0, null, NO_FILTERS));
  }

  @Test
  void list_withSeveralCountryCodesTrimsEachAndDropsBlanksAndNulls() {
    Address matching = persistedAddress("matching-id", ORG, AddressStatus.ACTIVE);
    ListCriteria expected = new ListCriteria(null, null, null, List.of("FR", "GB"), List.of());
    when(repository.listLiveAddresses(eq(ORG), eq(expected), any(Pageable.class)))
        .thenReturn(pageOf(List.of(matching), 0, DEFAULT_PAGE_SIZE, 1));

    OperatorPageResponse response =
        service.list(
            ORG, 1, null, filtersWithCountryCodes(Arrays.asList(" FR ", " ", null, "GB")));

    assertThat(response.items()).extracting(OperatorResponse::id).containsExactly("matching-id");
  }

  @Test
  void list_withOnlyBlankCountryCodesPassesNoCountryCodes() {
    when(repository.listLiveAddresses(eq(ORG), eq(NO_LIST_CRITERIA), any(Pageable.class)))
        .thenReturn(emptyFirstPage());

    OperatorPageResponse response =
        service.list(ORG, 1, null, filtersWithCountryCodes(List.of("  ")));

    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_passesTheSearchTermAsAPartialMatchPattern() {
    ListCriteria expected = new ListCriteria(null, null, ".*\\Qfarm\\E.*", List.of(), List.of());
    when(repository.listLiveAddresses(eq(ORG), eq(expected), any(Pageable.class)))
        .thenReturn(emptyFirstPage());

    OperatorPageResponse response = service.list(ORG, 1, null, filtersWithSearchTerm("farm"));

    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_trimsTheSearchTermBeforeBuildingThePattern() {
    Address matching = persistedAddress("matching-id", ORG, AddressStatus.ACTIVE);
    ListCriteria expected = new ListCriteria(null, null, ".*\\Qfarm\\E.*", List.of(), List.of());
    when(repository.listLiveAddresses(eq(ORG), eq(expected), any(Pageable.class)))
        .thenReturn(pageOf(List.of(matching), 0, DEFAULT_PAGE_SIZE, 1));

    OperatorPageResponse response = service.list(ORG, 1, null, filtersWithSearchTerm("  farm  "));

    assertThat(response.items()).extracting(OperatorResponse::id).containsExactly("matching-id");
  }

  @Test
  void list_withABlankSearchTermPassesNoPattern() {
    when(repository.listLiveAddresses(eq(ORG), eq(NO_LIST_CRITERIA), any(Pageable.class)))
        .thenReturn(emptyFirstPage());

    OperatorPageResponse response = service.list(ORG, 1, null, filtersWithSearchTerm("   "));

    assertThat(response.totalItems()).isZero();
  }

  @Test
  void list_passesTheSearchPatternAndTrimmedCountryCodesTogether() {
    ListCriteria expected =
        new ListCriteria(null, null, ".*\\QFrance\\E.*", List.of("FR"), List.of());
    when(repository.listLiveAddresses(eq(ORG), eq(expected), any(Pageable.class)))
        .thenReturn(emptyFirstPage());

    OperatorPageResponse response =
        service.list(ORG, 1, null, new RequestedListFilters("France", List.of(" FR "), null, null, null));

    assertThat(response.totalItems()).isZero();
  }

  @ParameterizedTest
  @EnumSource(AddressType.class)
  void list_passesTheAddressTypeWithTheSearchAndCountryCodes(AddressType addressType) {
    Address matching = persistedAddress("matching-id", ORG, AddressStatus.ACTIVE);
    ListCriteria expected =
        new ListCriteria(addressType, null, ".*\\Qfarm\\E.*", List.of("FR"), List.of());
    when(repository.listLiveAddresses(eq(ORG), eq(expected), any(Pageable.class)))
        .thenReturn(pageOf(List.of(matching), 0, DEFAULT_PAGE_SIZE, 1));

    OperatorPageResponse response =
        service.list(
            ORG, 1, null, new RequestedListFilters("farm", List.of("FR"), addressType, null, null));

    assertThat(response.items()).extracting(OperatorResponse::id).containsExactly("matching-id");
  }

  @Test
  void listCriteria_refusesANullCountryCodeList() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria(null, null, null, null, List.of()));
  }

  @Test
  void listCriteria_refusesANullSearchUseList() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria(null, null, null, List.of(), null));
  }
}
