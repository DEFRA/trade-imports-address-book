package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
}
