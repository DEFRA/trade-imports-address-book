package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.defra.trade.imports.operators.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.operators.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.operators.exceptions.ValidationException;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

  @Mock private OperatorRepository repository;

  @Captor private ArgumentCaptor<List<OperatorType>> typesCaptor;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private OperatorService service;

  @BeforeEach
  void setUp() {
    service = new OperatorService(repository, meterRegistry);
  }

  private OperatorRequest request() {
    return OperatorRequest.builder()
        .operatorType(OperatorType.CONSIGNOR)
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .addressLine2("Unit 3")
        .town("Inverness")
        .county("Highland")
        .postcode("IV2 3JH")
        .country("United Kingdom")
        .telephone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .build();
  }

  @Test
  void createStampsCrnOrganisationIdAndActiveStatusFromTheHeaders() {
    when(repository.save(any(Operator.class)))
        .thenAnswer(
            invocation -> {
              Operator saved = invocation.getArgument(0);
              saved.setId("665f1c2ab3e4d51a2c9d0e77");
              saved.setCreatedAt(Instant.parse("2026-07-14T09:15:27Z"));
              saved.setModifiedAt(Instant.parse("2026-07-14T09:15:27Z"));
              return saved;
            });

    Operator created = service.create(request(), "1100014934", "org-uuid-1");

    assertThat(created.getCrn()).isEqualTo("1100014934");
    assertThat(created.getOrganisationId()).isEqualTo("org-uuid-1");
    assertThat(created.getStatus()).isEqualTo(OperatorStatus.ACTIVE);
    assertThat(created.getName()).isEqualTo("Highland Livestock Ltd");
    assertThat(created.getCountry()).isEqualTo("United Kingdom");
    assertThat(created.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
  }

  @Test
  void createNeverSetsServerFieldsFromTheRequestAndLeavesIdForMongo() {
    ArgumentCaptor<Operator> captor = ArgumentCaptor.forClass(Operator.class);
    when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(request(), "1100014934", "org-uuid-1");

    Operator persisted = captor.getValue();
    // crn/organisationId/status come from the service, never the body; id is left for Mongo to
    // assign and timestamps for auditing.
    assertThat(persisted.getId()).isNull();
    assertThat(persisted.getStatus()).isEqualTo(OperatorStatus.ACTIVE);
    assertThat(persisted.getCrn()).isEqualTo("1100014934");
    assertThat(persisted.getOrganisationId()).isEqualTo("org-uuid-1");
    assertThat(persisted.getCreatedAt()).isNull();
    assertThat(persisted.getModifiedAt()).isNull();
  }

  private Operator persistedOperator(String id, String crn, OperatorStatus status) {
    return Operator.builder()
        .id(id)
        .operatorType(OperatorType.CONSIGNOR)
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .town("Inverness")
        .postcode("IV2 3JH")
        .country("United Kingdom")
        .telephone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .crn(crn)
        .organisationId("org-uuid-1")
        .status(status)
        .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
        .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
        .build();
  }

  @Test
  void getReturnsTheOperatorForTheOwningCrn() {
    Operator stored =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.ACTIVE);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(stored));

    Optional<Operator> found = service.get("665f1c2ab3e4d51a2c9d0e77", "1100014934");

    assertThat(found).contains(stored);
  }

  @Test
  void getForADifferentCrnReturnsEmptySoTheControllerCan404() {
    // The store scopes by crn: an id owned by another organisation is simply not found, exactly
    // as an unknown id is — the controller cannot tell the two apart, so 404 leaks no existence.
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "9900000000"))
        .thenReturn(Optional.empty());

    Optional<Operator> found = service.get("665f1c2ab3e4d51a2c9d0e77", "9900000000");

    assertThat(found).isEmpty();
  }

  @Test
  void getOfADeletedOperatorReturnsItWithStatusDeletedBecauseATombstoneIsFetchable() {
    // A soft-delete tombstone is readable by id (200 + status DELETED), NOT a 404 — this is the
    // EUDPA-293.AC2 detection surface: "deleted" and "unknown/not-yours" are different states.
    Operator tombstone =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.DELETED);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(tombstone));

    Optional<Operator> found = service.get("665f1c2ab3e4d51a2c9d0e77", "1100014934");

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(OperatorStatus.DELETED);
  }

  private OperatorRequest updateRequest(OperatorType operatorType) {
    return OperatorRequest.builder()
        .operatorType(operatorType)
        .name("Lowland Cattle Co")
        .addressLine1("2 Market Street")
        .addressLine2("Suite 5")
        .town("Perth")
        .county("Perth and Kinross")
        .postcode("PH1 5AA")
        .country("United Kingdom")
        .telephone("+44 1738 111222")
        .email("ops@lowlandcattle.example.com")
        .build();
  }

  @Test
  void updateAppliesTheNewFieldValuesBumpsModifiedAtAndPreservesTheServerOwnedFields() {
    Operator existing =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.ACTIVE);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(existing));
    Instant bumped = Instant.parse("2026-07-15T10:00:00Z");
    ArgumentCaptor<Operator> captor = ArgumentCaptor.forClass(Operator.class);
    when(repository.save(captor.capture()))
        .thenAnswer(
            invocation -> {
              Operator saved = invocation.getArgument(0);
              // simulate @LastModifiedDate auditing at persistence time
              saved.setModifiedAt(bumped);
              return saved;
            });

    Operator updated =
        service.update("665f1c2ab3e4d51a2c9d0e77", updateRequest(OperatorType.CONSIGNOR), "1100014934");

    // new mutable field values are returned and persisted
    assertThat(updated.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(updated.getAddressLine1()).isEqualTo("2 Market Street");
    assertThat(updated.getTown()).isEqualTo("Perth");
    assertThat(updated.getPostcode()).isEqualTo("PH1 5AA");
    assertThat(updated.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
    // modified_at is bumped (audit only — c-017)
    assertThat(updated.getModifiedAt()).isEqualTo(bumped);
    // server-owned fields are untouched by the wire
    assertThat(updated.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(updated.getOperatorType()).isEqualTo(OperatorType.CONSIGNOR);
    assertThat(updated.getCrn()).isEqualTo("1100014934");
    assertThat(updated.getOrganisationId()).isEqualTo("org-uuid-1");
    assertThat(updated.getStatus()).isEqualTo(OperatorStatus.ACTIVE);
    assertThat(updated.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));

    Operator persisted = captor.getValue();
    assertThat(persisted.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(persisted.getCrn()).isEqualTo("1100014934");
    assertThat(persisted.getStatus()).isEqualTo(OperatorStatus.ACTIVE);
    assertThat(persisted.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T09:15:27Z"));
  }

  @Test
  void updateWithADifferentOperatorTypeIsRejectedAsAValidationErrorKeyedOperatorType() {
    Operator existing =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.ACTIVE);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(existing));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () ->
                service.update(
                    "665f1c2ab3e4d51a2c9d0e77", updateRequest(OperatorType.IMPORTER), "1100014934"))
        .satisfies(
            ex ->
                assertThat(ex.getErrors())
                    .containsExactly(
                        org.assertj.core.api.Assertions.entry(
                            "operatorType", List.of("Operator type cannot be changed"))));
  }

  @Test
  void updateOfADeletedTombstoneIs404BecauseItIsOutsideTheCallersLiveSet() {
    Operator tombstone =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.DELETED);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(tombstone));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service.update(
                    "665f1c2ab3e4d51a2c9d0e77", updateRequest(OperatorType.CONSIGNOR), "1100014934"));
  }

  @Test
  void updateOfACrossCrnIdIs404BecauseTheStoreReturnsEmpty() {
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "9900000000"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service.update(
                    "665f1c2ab3e4d51a2c9d0e77", updateRequest(OperatorType.CONSIGNOR), "9900000000"));
  }

  @Test
  void deleteFlipsAnActiveOperatorToAdeletedTombstoneAndBumpsModifiedAt() {
    Operator existing =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.ACTIVE);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(existing));
    Instant bumped = Instant.parse("2026-07-15T10:00:00Z");
    ArgumentCaptor<Operator> captor = ArgumentCaptor.forClass(Operator.class);
    when(repository.save(captor.capture()))
        .thenAnswer(
            invocation -> {
              Operator saved = invocation.getArgument(0);
              // simulate @LastModifiedDate auditing at persistence time
              saved.setModifiedAt(bumped);
              return saved;
            });

    service.delete("665f1c2ab3e4d51a2c9d0e77", "1100014934");

    // the document is NOT removed — status flips to DELETED (the load-bearing tombstone, c-003/c-018)
    Operator persisted = captor.getValue();
    assertThat(persisted.getStatus()).isEqualTo(OperatorStatus.DELETED);
    assertThat(persisted.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(persisted.getModifiedAt()).isEqualTo(bumped);
  }

  @Test
  void deleteOfAnAlreadyDeletedTombstoneIsIdempotentAndLeavesTheTombstoneUntouched() {
    // A repeat delete is a no-op: the tombstone stays a tombstone with the SAME modified_at — no
    // re-save, so modified_at is not bumped a second time (design §1.2 "204 with no state change").
    Instant originalModifiedAt = Instant.parse("2026-07-14T09:15:27Z");
    Operator tombstone =
        persistedOperator("665f1c2ab3e4d51a2c9d0e77", "1100014934", OperatorStatus.DELETED);
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "1100014934"))
        .thenReturn(Optional.of(tombstone));

    service.delete("665f1c2ab3e4d51a2c9d0e77", "1100014934");

    // state unchanged: still a DELETED tombstone with its original modified_at
    assertThat(tombstone.getStatus()).isEqualTo(OperatorStatus.DELETED);
    assertThat(tombstone.getModifiedAt()).isEqualTo(originalModifiedAt);
  }

  @Test
  void deleteOfACrossCrnOrUnknownIdIs404BecauseTheStoreReturnsEmpty() {
    when(repository.findByIdAndCrn("665f1c2ab3e4d51a2c9d0e77", "9900000000"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete("665f1c2ab3e4d51a2c9d0e77", "9900000000"));
  }

  private List<Operator> activeOperators(int count) {
    List<Operator> operators = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      operators.add(persistedOperator("665f1c2ab3e4d51a2c9d0e" + i, "1100014934", OperatorStatus.ACTIVE));
    }
    return operators;
  }

  @Test
  void listReturnsAFullFirstPageOf25WithTotalPages2For30ActiveOperators() {
    when(repository.search(eq("1100014934"), anyList(), anyString(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeOperators(25), PageRequest.of(0, 25), 30));

    OperatorPageResponse response = service.list("1100014934", null, null, 1, 25);

    assertThat(response.items()).hasSize(25);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(25);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void listPageTwoReturnsTheRemaining5OperatorsWithTotalPagesStill2() {
    when(repository.search(eq("1100014934"), anyList(), anyString(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(activeOperators(5), PageRequest.of(1, 25), 30));

    OperatorPageResponse response = service.list("1100014934", null, null, 2, 25);

    assertThat(response.items()).hasSize(5);
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.totalItems()).isEqualTo(30);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void listScopesTheQueryToTheCallersCrnNewestFirstAndTranslatesToA0BasedPage() {
    // crn scoping is pinned by eq("1100014934"); ACTIVE-only lives inside the @Query (OperatorListIT
    // covers it); the captured Pageable pins newest-first + 1-based->0-based translation.
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.search(eq("1100014934"), anyList(), anyString(), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 25), 0));

    service.list("1100014934", null, null, 2, 25);

    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(25);
    assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void listWithoutAtypeQueriesAll7OperatorTypesAndWithoutAqUsesTheEmptyRegexSentinel() {
    // §1.3 sentinel convention: absent operator_type -> all 7 types ($in matches every operator),
    // absent q -> "" regex (matches everything). Documented in one place on OperatorService.list.
    ArgumentCaptor<String> regexCaptor = ArgumentCaptor.forClass(String.class);
    when(repository.search(
            eq("1100014934"), typesCaptor.capture(), regexCaptor.capture(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    service.list("1100014934", null, null, 1, 25);

    assertThat(typesCaptor.getValue()).containsExactlyInAnyOrder(OperatorType.values());
    assertThat(regexCaptor.getValue()).isEmpty();
  }

  @Test
  void listQuotesUserInputSoRegexMetacharactersAreLiteralAndFiltersToTheSingleGivenType() {
    // Pattern.quote() on the user input — ".*" is compiled as a literal, never as "match anything"
    // (no ReDoS, no regex-syntax 500s); a present operator_type narrows the $in to just that type.
    ArgumentCaptor<String> regexCaptor = ArgumentCaptor.forClass(String.class);
    when(repository.search(
            eq("1100014934"), typesCaptor.capture(), regexCaptor.capture(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

    service.list("1100014934", ".*", OperatorType.IMPORTER, 1, 25);

    assertThat(typesCaptor.getValue()).containsExactly(OperatorType.IMPORTER);
    assertThat(regexCaptor.getValue()).isEqualTo(Pattern.quote(".*"));
  }

  @Test
  void listWithAPageBelow1IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list("1100014934", null, null, 0, 25));
  }

  @Test
  void listWithAPageSizeAbove100IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list("1100014934", null, null, 1, 101));
  }

  @Test
  void listWithAPageSizeBelow1IsABadRequest() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.list("1100014934", null, null, 1, 0));
  }
}
