package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

  @Mock private OperatorRepository repository;

  @InjectMocks private OperatorService service;

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
}
