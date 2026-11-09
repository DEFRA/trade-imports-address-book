package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uk.gov.defra.trade.imports.addressbook.ListPaging.DEFAULT_PAGE_SIZE;
import static uk.gov.defra.trade.imports.addressbook.PageTestSupport.pageOf;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.NO_TTL;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.STORED_ADDRESS_ID;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.STORED_CREATED_AT;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.STORED_MODIFIED_AT;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.replacementRequest;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.storedAddress;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.validRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.addressbook.metrics.AddressMetrics;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

  private static final String ORG = "org-uuid-1";

  @Mock private AddressRepository repository;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final AddressMetrics addressMetrics = new AddressMetrics(meterRegistry);
  private final AddressMapper addressMapper = Mappers.getMapper(AddressMapper.class);

  private AddressService service;

  @BeforeEach
  void setUp() {
    service =
        new AddressService(repository, addressMapper, addressMetrics, NO_TTL);
  }

  private AddressService serviceWithTtl(Integer days, String environment) {
    return new AddressService(
        repository,
        addressMapper,
        addressMetrics,
        new AddressTtlConfig(days, environment));
  }

  private double changesCounted(String change) {
    var counter = meterRegistry.find("address.change").tag("change", change).counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  void create_setsTheGivenOrganisationIdAndActiveStatus() {
    when(repository.save(any(Address.class)))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setId(STORED_ADDRESS_ID);
              saved.setCreatedAt(Instant.parse("2026-07-14T09:15:27Z"));
              saved.setModifiedAt(Instant.parse("2026-07-14T09:15:27Z"));
              return saved;
            });

    Address created = service.create(validRequest().build(), ORG);

    assertThat(created.getOrganisationId()).isEqualTo(ORG);
    assertThat(created.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(created.getName()).isEqualTo("Highland Livestock Ltd");
    assertThat(created.getCountryCode()).isEqualTo("GB");
  }

  @Test
  void create_logsTheNewAddressIdAndOrganisationId() {
    when(repository.save(any(Address.class)))
        .thenAnswer(
            invocation -> {
              Address saved = invocation.getArgument(0);
              saved.setId(STORED_ADDRESS_ID);
              return saved;
            });
    AddressRequest request = validRequest().build();

    List<ILoggingEvent> events =
        loggedBy(AddressService.class, () -> service.create(request, ORG));

    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly("Created address " + STORED_ADDRESS_ID + " in organisation " + ORG);
  }

  @Test
  void create_countsOneCreate() {
    when(repository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(validRequest().build(), ORG);

    assertThat(changesCounted("create")).isEqualTo(1);
  }

  @Test
  void create_neverSetsServerFieldsFromTheRequestAndLeavesIdForMongo() {
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(validRequest().build(), ORG);

    Address persisted = captor.getValue();
    assertThat(persisted.getId()).isNull();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getCreatedAt()).isNull();
    assertThat(persisted.getModifiedAt()).isNull();
  }

  @Test
  void create_inANonProdEnvironmentWithTtlDaysSetsExpireAtThatManyDaysAhead() {
    AddressService withTtl = serviceWithTtl(7, "dev");
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
    Instant before = Instant.now();

    withTtl.create(validRequest().build(), ORG);

    Instant after = Instant.now();
    assertThat(captor.getValue().getExpireAt())
        .isBetween(before.plus(7, ChronoUnit.DAYS), after.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void create_withNoTtlDaysConfiguredLeavesExpireAtUnset() {
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(validRequest().build(), ORG);

    assertThat(captor.getValue().getExpireAt()).isNull();
  }

  @Test
  void create_inProdNeverSetsExpireAtEvenWithTtlDaysConfigured() {
    AddressService inProd = serviceWithTtl(7, "prod");
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    inProd.create(validRequest().build(), ORG);

    assertThat(captor.getValue().getExpireAt()).isNull();
  }

  @Test
  void get_returnsTheAddressForTheOwningOrganisation() {
    Address stored = storedAddress(ORG, AddressStatus.ACTIVE).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(stored));

    Optional<Address> found = service.get(STORED_ADDRESS_ID, ORG);

    assertThat(found).contains(stored);
  }

  @Test
  void get_forAnotherOrganisationsIdReturnsEmpty() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, "org-other"))
        .thenReturn(Optional.empty());

    Optional<Address> found = service.get(STORED_ADDRESS_ID, "org-other");

    assertThat(found).isEmpty();
  }

  @Test
  void get_ofADeletedAddressReturnsItWithStatusDeleted() {
    Address tombstone = storedAddress(ORG, AddressStatus.DELETED).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(tombstone));

    Optional<Address> found = service.get(STORED_ADDRESS_ID, ORG);

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(AddressStatus.DELETED);
  }

  @Test
  void update_appliesAllRequestFieldValuesAndPreservesTheServerOwnedFields() {
    Address existing = storedAddress(ORG, AddressStatus.ACTIVE).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    Address updated = service.update(STORED_ADDRESS_ID, replacementRequest().build(), ORG);

    assertThat(updated.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(updated.getAddressLine1()).isEqualTo("2 Market Street");
    assertThat(updated.getAddressLine2()).isEqualTo("Suite 5");
    assertThat(updated.getTownOrCity()).isEqualTo("Perth");
    assertThat(updated.getCounty()).isEqualTo("Perth and Kinross");
    assertThat(updated.getPostcode()).isEqualTo("PH1 5AA");
    assertThat(updated.getCountryCode()).isEqualTo("IE");
    assertThat(updated.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(updated.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
    assertThat(updated.getRoles()).containsExactly(AddressRole.BRANCH_ADDRESS);
    assertThat(updated.getId()).isEqualTo(STORED_ADDRESS_ID);
    assertThat(updated.getOrganisationId()).isEqualTo(ORG);
    assertThat(updated.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(updated.getCreatedAt()).isEqualTo(STORED_CREATED_AT);

    Address persisted = captor.getValue();
    assertThat(persisted.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(persisted.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(persisted.getId()).isEqualTo(STORED_ADDRESS_ID);
    assertThat(persisted.getOrganisationId()).isEqualTo(ORG);
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(persisted.getCreatedAt()).isEqualTo(STORED_CREATED_AT);
  }

  @Test
  void update_countsOneUpdate() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.ACTIVE).build()));
    when(repository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.update(STORED_ADDRESS_ID, replacementRequest().build(), ORG);

    assertThat(changesCounted("update")).isEqualTo(1);
  }

  @Test
  void update_ofADeletedAddressCountsNoUpdate() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.DELETED).build()));
    AddressRequest request = replacementRequest().build();

    catchThrowable(() -> service.update(STORED_ADDRESS_ID, request, ORG));

    assertThat(changesCounted("update")).isZero();
  }

  @Test
  void update_keepsTheExpireAtSetWhenTheAddressWasCreated() {
    AddressService withTtl = serviceWithTtl(7, "dev");
    Address existing = storedAddress(ORG, AddressStatus.ACTIVE).build();
    existing.setExpireAt(Instant.parse("2026-07-21T09:15:27Z"));
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    withTtl.update(STORED_ADDRESS_ID, replacementRequest().build(), ORG);

    assertThat(captor.getValue().getExpireAt()).isEqualTo(Instant.parse("2026-07-21T09:15:27Z"));
  }

  @Test
  void update_ofAnAddressWithNoExpireAtLeavesItUnsetEvenWithTtlDaysConfigured() {
    AddressService withTtl = serviceWithTtl(7, "dev");
    Address existing = storedAddress(ORG, AddressStatus.ACTIVE).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    withTtl.update(STORED_ADDRESS_ID, replacementRequest().build(), ORG);

    assertThat(captor.getValue().getExpireAt()).isNull();
  }

  @Test
  void update_ofADeletedAddressThrowsNotFound() {
    Address tombstone = storedAddress(ORG, AddressStatus.DELETED).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(tombstone));
    AddressRequest request = replacementRequest().build();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(STORED_ADDRESS_ID, request, ORG))
        .withMessage("Address " + STORED_ADDRESS_ID + " not found in organisation " + ORG);
  }

  @Test
  void update_ofAnotherOrganisationsIdThrowsNotFound() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, "org-other"))
        .thenReturn(Optional.empty());
    AddressRequest request = replacementRequest().build();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(STORED_ADDRESS_ID, request, "org-other"))
        .withMessage("Address " + STORED_ADDRESS_ID + " not found in organisation org-other");
  }

  @Test
  void update_logsTheAddressIdAndOrganisationId() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.ACTIVE).build()));
    when(repository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
    AddressRequest request = replacementRequest().build();

    List<ILoggingEvent> events =
        loggedBy(AddressService.class, () -> service.update(STORED_ADDRESS_ID, request, ORG));

    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly("Updated address " + STORED_ADDRESS_ID + " in organisation " + ORG);
  }

  @Test
  void delete_ofAnActiveAddressLogsTheAddressIdAndOrganisationId() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.ACTIVE).build()));
    when(repository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

    List<ILoggingEvent> events =
        loggedBy(AddressService.class, () -> service.delete(STORED_ADDRESS_ID, ORG));

    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly("Soft-deleted address " + STORED_ADDRESS_ID + " in organisation " + ORG);
  }

  @Test
  void delete_ofAnAlreadyDeletedAddressLogsThatItWasLeftUnchanged() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.DELETED).build()));

    List<ILoggingEvent> events =
        loggedBy(AddressService.class, () -> service.delete(STORED_ADDRESS_ID, ORG));

    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Address "
                + STORED_ADDRESS_ID
                + " in organisation "
                + ORG
                + " already deleted, left unchanged");
  }

  @Test
  void delete_flipsAnActiveAddressToADeletedTombstone() {
    Address existing = storedAddress(ORG, AddressStatus.ACTIVE).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(existing));
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.delete(STORED_ADDRESS_ID, ORG);

    Address persisted = captor.getValue();
    assertThat(persisted.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(persisted.getId()).isEqualTo(STORED_ADDRESS_ID);
  }

  @Test
  void delete_isIdempotentForAnAlreadyDeletedTombstone() {
    Address tombstone = storedAddress(ORG, AddressStatus.DELETED).build();
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(tombstone));

    service.delete(STORED_ADDRESS_ID, ORG);

    verify(repository, never()).save(any());
    assertThat(tombstone.getStatus()).isEqualTo(AddressStatus.DELETED);
    assertThat(tombstone.getModifiedAt()).isEqualTo(STORED_MODIFIED_AT);
  }

  @Test
  void delete_ofAnActiveAddressCountsOneDelete() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.ACTIVE).build()));
    when(repository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.delete(STORED_ADDRESS_ID, ORG);

    assertThat(changesCounted("delete")).isEqualTo(1);
  }

  @Test
  void delete_ofAnAlreadyDeletedAddressCountsNoDelete() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, ORG))
        .thenReturn(Optional.of(storedAddress(ORG, AddressStatus.DELETED).build()));

    service.delete(STORED_ADDRESS_ID, ORG);

    assertThat(changesCounted("delete")).isZero();
  }

  @Test
  void delete_ofAnUnknownOrAnotherOrganisationsIdThrowsNotFound() {
    when(repository.findByIdAndOrganisationId(STORED_ADDRESS_ID, "org-other"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(STORED_ADDRESS_ID, "org-other"))
        .withMessage("Address " + STORED_ADDRESS_ID + " not found in organisation org-other");
  }

  private List<Address> activeAddresses(int count) {
    List<Address> addresses = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      addresses.add(
          storedAddress(ORG, AddressStatus.ACTIVE).id("665f1c2ab3e4d51a2c9d0e" + i).build());
    }
    return addresses;
  }

  private Page<Address> emptyFirstPage() {
    return pageOf(List.of(), 0, DEFAULT_PAGE_SIZE, 0);
  }

  private static final RequestedListFilters NO_FILTERS =
      new RequestedListFilters(null, null, null, null);

  private static RequestedListFilters filtersWithSearchTerm(String searchTerm) {
    return new RequestedListFilters(searchTerm, null, null, null);
  }

  private static RequestedListFilters filtersWithCountryCodes(List<String> countryCodes) {
    return new RequestedListFilters(null, countryCodes, null, null);
  }

  @Test
  void list_returnsAFullFirstPageWithTotalPages2For30ActiveAddresses() {
    when(repository.listLiveAddresses(eq(ORG), any(ListCriteria.class), any(Pageable.class)))
        .thenReturn(pageOf(activeAddresses(DEFAULT_PAGE_SIZE), 0, DEFAULT_PAGE_SIZE, 30));

    AddressPageResponse response = service.list(ORG, 1, null, NO_FILTERS);

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

    AddressPageResponse response = service.list(ORG, 2, null, NO_FILTERS);

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

    AddressPageResponse response = service.list(ORG, 1, requestedPageSize, NO_FILTERS);

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


  private ListCriteria criteriaPassedFor(RequestedListFilters filters) {
    ArgumentCaptor<ListCriteria> criteriaCaptor = ArgumentCaptor.forClass(ListCriteria.class);
    when(repository.listLiveAddresses(eq(ORG), criteriaCaptor.capture(), any(Pageable.class)))
        .thenReturn(emptyFirstPage());

    service.list(ORG, 1, null, filters);

    return criteriaCaptor.getValue();
  }

  @Test
  void list_withSeveralCountryCodesTrimsEachAndDropsBlanksAndNulls() {
    ListCriteria passed =
        criteriaPassedFor(filtersWithCountryCodes(Arrays.asList(" FR ", " ", null, "GB")));

    assertThat(passed)
        .isEqualTo(
            new ListCriteria(
                List.of(), List.of(new ListCriteria.InCountries(List.of("FR", "GB")))));
  }

  @Test
  void list_withOnlyBlankCountryCodesPassesNoCountryCodes() {
    ListCriteria passed = criteriaPassedFor(filtersWithCountryCodes(List.of("  ")));

    assertThat(passed).isEqualTo(new ListCriteria(List.of(), List.of()));
  }

  @Test
  void list_passesTheSearchTermAsAPartialMatchPattern() {
    ListCriteria passed = criteriaPassedFor(filtersWithSearchTerm("farm"));

    assertThat(passed)
        .isEqualTo(
            new ListCriteria(List.of(), List.of(new ListCriteria.TextMatching(".*\\Qfarm\\E.*"))));
  }

  @Test
  void list_trimsTheSearchTermBeforeBuildingThePattern() {
    ListCriteria passed = criteriaPassedFor(filtersWithSearchTerm("  farm  "));

    assertThat(passed)
        .isEqualTo(
            new ListCriteria(List.of(), List.of(new ListCriteria.TextMatching(".*\\Qfarm\\E.*"))));
  }

  @Test
  void list_withABlankSearchTermPassesNoPattern() {
    ListCriteria passed = criteriaPassedFor(filtersWithSearchTerm("   "));

    assertThat(passed).isEqualTo(new ListCriteria(List.of(), List.of()));
  }

  @Test
  void list_passesTheSearchPatternAndTrimmedCountryCodesTogether() {
    ListCriteria passed =
        criteriaPassedFor(new RequestedListFilters("France", List.of(" FR "), null, null));

    assertThat(passed)
        .isEqualTo(
            new ListCriteria(
                List.of(),
                List.of(
                    new ListCriteria.TextMatching(".*\\QFrance\\E.*"),
                    new ListCriteria.InCountries(List.of("FR")))));
  }

  @Test
  void list_passesTheRolesToKeepWithoutNullsOrRepeats() {
    ListCriteria passed =
        criteriaPassedFor(
            new RequestedListFilters(
                null,
                null,
                Arrays.asList(
                    AddressRole.CONSIGNEE, null, AddressRole.IMPORTER, AddressRole.CONSIGNEE),
                null));

    assertThat(passed)
        .isEqualTo(
            new ListCriteria(List.of(AddressRole.CONSIGNEE, AddressRole.IMPORTER), List.of()));
  }

  @Test
  void list_passesTheSearchRolesAsOneMatch() {
    ListCriteria passed =
        criteriaPassedFor(
            new RequestedListFilters(
                null, null, null, Arrays.asList(AddressRole.CONSIGNOR, null, AddressRole.IMPORTER)));

    assertThat(passed)
        .isEqualTo(
            new ListCriteria(
                List.of(),
                List.of(
                    new ListCriteria.HoldingAnyRole(
                        List.of(AddressRole.CONSIGNOR, AddressRole.IMPORTER)))));
  }
}
