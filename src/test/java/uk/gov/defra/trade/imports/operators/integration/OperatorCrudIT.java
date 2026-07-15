package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;
import uk.gov.defra.trade.imports.operators.operator.OperatorStatus;

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
}
