package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.operators.operator.Operator;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;
import uk.gov.defra.trade.imports.operators.operator.OperatorStatus;
import uk.gov.defra.trade.imports.operators.operator.OperatorType;

/**
 * Full-stack CRUD integration test for {@code /operators}. This increment (inc-005) covers the
 * create leg: 201 + {@code Location} + the server-stamped identity, and the 400 validation problem
 * whose {@code errors} map is keyed by the snake_case wire field names — the end-to-end pin that
 * the field-level and cross-field constraints surface through the real handler.
 */
class OperatorCrudIT extends IntegrationBase {

  private static final String CRN = "1100014934";
  private static final String ORGANISATION_ID = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void createReturns201WithLocationAndServerStampedIdentity() throws Exception {
    String body =
        """
        {
          "operator_type": "CONSIGNOR",
          "name": "Highland Livestock Ltd",
          "address_line_1": "14 Drover's Way",
          "address_line_2": "Unit 3",
          "town": "Inverness",
          "county": "Highland",
          "postcode": "IV2 3JH",
          "country": "United Kingdom",
          "telephone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com"
        }
        """;

    mockMvc
        .perform(
            post("/operators")
                .header("Trade-Imports-Crn", CRN)
                .header("Trade-Imports-Organisation-Id", ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/operators/")))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.operator_type").value("CONSIGNOR"))
        .andExpect(jsonPath("$.name").value("Highland Livestock Ltd"))
        .andExpect(jsonPath("$.address_line_1").value("14 Drover's Way"))
        .andExpect(jsonPath("$.address_line_2").value("Unit 3"))
        .andExpect(jsonPath("$.country").value("United Kingdom"))
        .andExpect(jsonPath("$.crn").value(CRN))
        .andExpect(jsonPath("$.organisation_id").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.created_at").exists())
        .andExpect(jsonPath("$.modified_at").exists());

    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            operator -> {
              assertThat(operator.getCrn()).isEqualTo(CRN);
              assertThat(operator.getOrganisationId()).isEqualTo(ORGANISATION_ID);
              assertThat(operator.getStatus()).isEqualTo(OperatorStatus.ACTIVE);
              assertThat(operator.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void createReturns400ValidationProblemKeyedBySnakeCaseFieldNames() throws Exception {
    // address_line_1 blank, email malformed, approval_number supplied on a non-TRANSPORTER type.
    String body =
        """
        {
          "operator_type": "CONSIGNOR",
          "name": "Highland Livestock Ltd",
          "address_line_1": "",
          "town": "Inverness",
          "postcode": "IV2 3JH",
          "country": "United Kingdom",
          "telephone": "+44 1463 234567",
          "email": "not-an-email",
          "approval_number": "APR-123"
        }
        """;

    mockMvc
        .perform(
            post("/operators")
                .header("Trade-Imports-Crn", CRN)
                .header("Trade-Imports-Organisation-Id", ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors.address_line_1").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(
            jsonPath("$.errors.approval_number[0]").value("Only allowed for transporter operators"))
        // never the camelCase Java identifier
        .andExpect(jsonPath("$.errors.addressLine1").doesNotExist());

    assertThat(repository.findAll()).isEmpty();
  }

  private Operator saveOperator(OperatorStatus status) {
    Operator operator =
        Operator.builder()
            .operatorType(OperatorType.CONSIGNOR)
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .town("Inverness")
            .postcode("IV2 3JH")
            .country("United Kingdom")
            .telephone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .crn(CRN)
            .organisationId(ORGANISATION_ID)
            .status(status)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(operator);
  }

  @Test
  void getReturns200WithTheOperatorAndItsStatusField() throws Exception {
    Operator saved = saveOperator(OperatorStatus.ACTIVE);

    mockMvc
        .perform(get("/operators/{operator-id}", saved.getId()).header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value("Highland Livestock Ltd"))
        .andExpect(jsonPath("$.country").value("United Kingdom"))
        .andExpect(jsonPath("$.crn").value(CRN))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void getOfADeletedTombstoneReturns200WithStatusDeletedNotA404() throws Exception {
    // EUDPA-293.AC2: a tombstone stays FETCHABLE so a consumer can detect "the user deleted this"
    // (200 + DELETED) as distinct from "unknown / not yours" (404).
    Operator saved = saveOperator(OperatorStatus.DELETED);

    mockMvc
        .perform(get("/operators/{operator-id}", saved.getId()).header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.status").value("DELETED"));
  }

  @Test
  void getOfAnUnknownIdReturns404NotFoundProblem() throws Exception {
    mockMvc
        .perform(get("/operators/{operator-id}", "665f1c2ab3e4d51a2c9d0e77").header("Trade-Imports-Crn", CRN))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        // 404 carries no errors map — it is not a validation failure.
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  private static final String UPDATE_BODY =
      """
      {
        "operator_type": "CONSIGNOR",
        "name": "Lowland Cattle Co",
        "address_line_1": "2 Market Street",
        "town": "Perth",
        "postcode": "PH1 5AA",
        "country": "United Kingdom",
        "telephone": "+44 1738 111222",
        "email": "ops@lowlandcattle.example.com"
      }
      """;

  @Test
  void putReplacesTheMutableFieldsReturns200AndBumpsModifiedAt() throws Exception {
    Operator saved = saveOperator(OperatorStatus.ACTIVE);
    Instant baselineModifiedAt = saved.getModifiedAt();

    mockMvc
        .perform(
            put("/operators/{operator-id}", saved.getId())
                .header("Trade-Imports-Crn", CRN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"))
        .andExpect(jsonPath("$.address_line_1").value("2 Market Street"))
        .andExpect(jsonPath("$.town").value("Perth"))
        .andExpect(jsonPath("$.operator_type").value("CONSIGNOR"))
        .andExpect(jsonPath("$.crn").value(CRN))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(
            operator -> {
              assertThat(operator.getName()).isEqualTo("Lowland Cattle Co");
              assertThat(operator.getAddressLine1()).isEqualTo("2 Market Street");
              assertThat(operator.getStatus()).isEqualTo(OperatorStatus.ACTIVE);
              // audit-only bump; nothing re-syncs off it (c-017)
              assertThat(operator.getModifiedAt()).isAfter(baselineModifiedAt);
            });
  }

  @Test
  void putChangingTheOperatorTypeReturns400ValidationProblemKeyedOperatorType() throws Exception {
    Operator saved = saveOperator(OperatorStatus.ACTIVE); // stored type is CONSIGNOR
    String typeChangeBody = UPDATE_BODY.replace("\"CONSIGNOR\"", "\"IMPORTER\"");

    mockMvc
        .perform(
            put("/operators/{operator-id}", saved.getId())
                .header("Trade-Imports-Crn", CRN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(typeChangeBody))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors.operator_type[0]").value("Operator type cannot be changed"));

    // the stored operator is untouched by the rejected type change
    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(operator -> assertThat(operator.getOperatorType()).isEqualTo(OperatorType.CONSIGNOR));
  }

  @Test
  void putOnADeletedTombstoneReturns404() throws Exception {
    Operator tombstone = saveOperator(OperatorStatus.DELETED);

    mockMvc
        .perform(
            put("/operators/{operator-id}", tombstone.getId())
                .header("Trade-Imports-Crn", CRN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void fullCrudRoundTripCreateGetPutDeleteTombstoneAndIdempotentRepeatDelete() throws Exception {
    // create (201 + Location)
    String body =
        """
        {
          "operator_type": "CONSIGNOR",
          "name": "Highland Livestock Ltd",
          "address_line_1": "14 Drover's Way",
          "town": "Inverness",
          "postcode": "IV2 3JH",
          "country": "United Kingdom",
          "telephone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com"
        }
        """;
    String location =
        mockMvc
            .perform(
                post("/operators")
                    .header("Trade-Imports-Crn", CRN)
                    .header("Trade-Imports-Organisation-Id", ORGANISATION_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    String id = location.substring(location.lastIndexOf('/') + 1);

    // get -> ACTIVE
    mockMvc
        .perform(get("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    // put -> 200, modified_at bumped
    mockMvc
        .perform(
            put("/operators/{operator-id}", id)
                .header("Trade-Imports-Crn", CRN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"));

    // delete -> 204, the document is NOT removed
    mockMvc
        .perform(delete("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN))
        .andExpect(status().isNoContent());
    assertThat(repository.findById(id)).isPresent();
    Instant modifiedAtAfterDelete = repository.findById(id).orElseThrow().getModifiedAt();

    // get -> the status:DELETED tombstone is still fetchable (EUDPA-293.AC2 / c-018)
    mockMvc
        .perform(get("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("DELETED"));

    // put on the tombstone -> 404 (outside the caller's live set)
    mockMvc
        .perform(
            put("/operators/{operator-id}", id)
                .header("Trade-Imports-Crn", CRN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isNotFound());

    // repeat delete -> 204, idempotent, no state change (modified_at not bumped again)
    mockMvc
        .perform(delete("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN))
        .andExpect(status().isNoContent());
    assertThat(repository.findById(id))
        .get()
        .satisfies(
            operator -> {
              assertThat(operator.getStatus()).isEqualTo(OperatorStatus.DELETED);
              assertThat(operator.getModifiedAt()).isEqualTo(modifiedAtAfterDelete);
            });
  }

  @Test
  void deleteOfAnUnknownIdReturns404NotFoundProblem() throws Exception {
    mockMvc
        .perform(
            delete("/operators/{operator-id}", "665f1c2ab3e4d51a2c9d0e77")
                .header("Trade-Imports-Crn", CRN))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void deleteOfAnOperatorOwnedByAnotherCrnReturns404() throws Exception {
    Operator saved = saveOperator(OperatorStatus.ACTIVE);

    mockMvc
        .perform(
            delete("/operators/{operator-id}", saved.getId())
                .header("Trade-Imports-Crn", "9900000000"))
        .andExpect(status().isNotFound());

    // the operator remains untouched under its owning crn
    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(operator -> assertThat(operator.getStatus()).isEqualTo(OperatorStatus.ACTIVE));
  }

  @Test
  void putWithoutTheCrnHeaderReturns400BadRequestWithNoErrorsMap() throws Exception {
    Operator saved = saveOperator(OperatorStatus.ACTIVE);

    mockMvc
        .perform(
            put("/operators/{operator-id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        // a missing identity header is not a field-validation failure — no errors map.
        .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
