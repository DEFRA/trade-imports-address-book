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
import uk.gov.defra.trade.imports.operators.operator.Address;
import uk.gov.defra.trade.imports.operators.operator.AddressStatus;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Full-stack CRUD integration test for {@code /operators}. Covers the create leg (201 +
 * {@code Location} + the server-stamped organisationId), the camelCase wire, the derived
 * {@code deleted} boolean tombstone signal, and the 400 validation problem whose {@code errors} map
 * is keyed by the camelCase wire field names.
 */
class OperatorCrudIT extends IntegrationBase {

  private static final String ORGANISATION_ID = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void createReturns201WithLocationAndServerStampedOrganisation() throws Exception {
    String body =
        """
        {
          "name": "Highland Livestock Ltd",
          "addressLine1": "14 Drover's Way",
          "addressLine2": "Unit 3",
          "townOrCity": "Inverness",
          "county": "Highland",
          "postcode": "IV2 3JH",
          "countryCode": "GB",
          "phone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com"
        }
        """;

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", startsWith("/organisation/" + ORGANISATION_ID + "/addresses/")))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Highland Livestock Ltd"))
        .andExpect(jsonPath("$.addressLine1").value("14 Drover's Way"))
        .andExpect(jsonPath("$.addressLine2").value("Unit 3"))
        .andExpect(jsonPath("$.townOrCity").value("Inverness"))
        .andExpect(jsonPath("$.countryCode").value("GB"))
        .andExpect(jsonPath("$.phone").value("+44 1463 234567"))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.modifiedAt").exists());

    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            address -> {
              assertThat(address.getOrganisationId()).isEqualTo(ORGANISATION_ID);
              assertThat(address.getStatus()).isEqualTo(AddressStatus.ACTIVE);
              assertThat(address.getCountryCode()).isEqualTo("GB");
              assertThat(address.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void createReturns400ValidationProblemKeyedByCamelCaseFieldNames() throws Exception {
    // addressLine1 blank, email malformed.
    String body =
        """
        {
          "name": "Highland Livestock Ltd",
          "addressLine1": "",
          "townOrCity": "Inverness",
          "postcode": "IV2 3JH",
          "countryCode": "GB",
          "phone": "+44 1463 234567",
          "email": "not-an-email"
        }
        """;

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        // never the snake_case form
        .andExpect(jsonPath("$.errors.address_line_1").doesNotExist());

    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void createRejectsAStrayTypeOrRoleFieldWithAPerFieldError() throws Exception {
    // cv-044: the book is untyped/unroled — a supplied type/role is a per-field 400, keyed by the
    // field, not a silent drop and not a deserialization failure.
    String body =
        """
        {
          "name": "Highland Livestock Ltd",
          "addressLine1": "14 Drover's Way",
          "townOrCity": "Inverness",
          "postcode": "IV2 3JH",
          "countryCode": "GB",
          "phone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com",
          "type": "IMPORTER",
          "role": "consignor"
        }
        """;

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.errors.type").exists())
        .andExpect(jsonPath("$.errors.role").exists());

    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void createIgnoresEchoedReadOnlyAndUnrelatedUnknownFieldsRatherThanRejectingThem() throws Exception {
    // An echoed read-only field (id/createdAt from a prior GET) and an unrelated unknown field are
    // silently dropped (Zalando failOnUnknownProperties(false)) — only type/role are rejected.
    String body =
        """
        {
          "name": "Highland Livestock Ltd",
          "addressLine1": "14 Drover's Way",
          "townOrCity": "Inverness",
          "postcode": "IV2 3JH",
          "countryCode": "GB",
          "phone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com",
          "id": "echoed-read-only-id",
          "createdAt": "2020-01-01T00:00:00Z",
          "deleted": true,
          "somethingUnknown": "ignored"
        }
        """;

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        // the server assigns id/deleted — the echoed values are ignored
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not("echoed-read-only-id")))
        .andExpect(jsonPath("$.deleted").value(false));

    assertThat(repository.findAll()).singleElement();
  }

  @Test
  void createAcceptsCountryCodeAsGivenAndAFreeStringPhone() throws Exception {
    // cv-011: countryCode is stored exactly as given (no list check). cv-044: phone is not
    // format-validated, so a free string is accepted.
    String body =
        """
        {
          "name": "Ferme des Deux Rivieres",
          "addressLine1": "12 Rue du Marche",
          "townOrCity": "Calais",
          "postcode": "62100",
          "countryCode": "FR",
          "phone": "ring the office",
          "email": "exports@deuxrivieres.example.com"
        }
        """;

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.countryCode").value("FR"))
        .andExpect(jsonPath("$.phone").value("ring the office"));

    assertThat(repository.findAll())
        .singleElement()
        .satisfies(address -> assertThat(address.getCountryCode()).isEqualTo("FR"));
  }

  @Test
  void createWithABlankCountryCodeReturns400() throws Exception {
    String body =
        """
        {
          "name": "Highland Livestock Ltd",
          "addressLine1": "14 Drover's Way",
          "townOrCity": "Inverness",
          "postcode": "IV2 3JH",
          "countryCode": "",
          "phone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com"
        }
        """;

    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.errors.countryCode").exists());

    assertThat(repository.findAll()).isEmpty();
  }

  private Address saveAddress(AddressStatus status) {
    Address address =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(ORGANISATION_ID)
            .status(status)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(address);
  }

  @Test
  void getReturns200WithTheAddressAndItsDeletedFalseSignal() throws Exception {
    Address saved = saveAddress(AddressStatus.ACTIVE);

    mockMvc
        .perform(get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId()).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value("Highland Livestock Ltd"))
        .andExpect(jsonPath("$.countryCode").value("GB"))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void getOfADeletedTombstoneReturns200WithDeletedTrueNotA404() throws Exception {
    // EUDPA-293.AC2: a tombstone stays FETCHABLE so a consumer can detect "the user deleted this"
    // (200 + deleted true) as distinct from "unknown / not yours" (404).
    Address saved = saveAddress(AddressStatus.DELETED);

    mockMvc
        .perform(get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId()).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void getOfAnUnknownIdReturns404NotFoundProblem() throws Exception {
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, "665f1c2ab3e4d51a2c9d0e77")
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  private static final String UPDATE_BODY =
      """
      {
        "name": "Lowland Cattle Co",
        "addressLine1": "2 Market Street",
        "townOrCity": "Perth",
        "postcode": "PH1 5AA",
        "countryCode": "IE",
        "phone": "+44 1738 111222",
        "email": "ops@lowlandcattle.example.com"
      }
      """;

  @Test
  void putReplacesTheMutableFieldsReturns200AndBumpsModifiedAt() throws Exception {
    Address saved = saveAddress(AddressStatus.ACTIVE);
    Instant baselineModifiedAt = saved.getModifiedAt();

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"))
        .andExpect(jsonPath("$.addressLine1").value("2 Market Street"))
        .andExpect(jsonPath("$.townOrCity").value("Perth"))
        .andExpect(jsonPath("$.countryCode").value("IE"))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.deleted").value(false));

    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getName()).isEqualTo("Lowland Cattle Co");
              assertThat(address.getAddressLine1()).isEqualTo("2 Market Street");
              assertThat(address.getStatus()).isEqualTo(AddressStatus.ACTIVE);
              assertThat(address.getModifiedAt()).isAfter(baselineModifiedAt);
            });
  }

  @Test
  void putClearsAnOmittedOptionalFieldBecauseItIsAFullReplace() throws Exception {
    Address saved = saveAddress(AddressStatus.ACTIVE);
    saved.setCounty("Highland");
    repository.save(saved);

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk());

    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(address -> assertThat(address.getCounty()).isNull());
  }

  @Test
  void putOnADeletedTombstoneReturns404() throws Exception {
    Address tombstone = saveAddress(AddressStatus.DELETED);

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, tombstone.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
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
    String body =
        """
        {
          "name": "Highland Livestock Ltd",
          "addressLine1": "14 Drover's Way",
          "townOrCity": "Inverness",
          "postcode": "IV2 3JH",
          "countryCode": "GB",
          "phone": "+44 1463 234567",
          "email": "exports@highlandlivestock.example.com"
        }
        """;
    String location =
        mockMvc
            .perform(
                post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                    .header(ORG_HEADER, ORGANISATION_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    String id = location.substring(location.lastIndexOf('/') + 1);

    // get -> deleted false
    mockMvc
        .perform(get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));

    // put -> 200
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"));

    // delete -> 204, the document is NOT removed
    mockMvc
        .perform(delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());
    assertThat(repository.findById(id)).isPresent();
    Instant modifiedAtAfterDelete = repository.findById(id).orElseThrow().getModifiedAt();

    // get -> the deleted tombstone is still fetchable (EUDPA-293.AC2)
    mockMvc
        .perform(get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.deleted").value(true));

    // put on the tombstone -> 404 (outside the caller's live set)
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isNotFound());

    // repeat delete -> 204, idempotent, no state change (modifiedAt not bumped again)
    mockMvc
        .perform(delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());
    assertThat(repository.findById(id))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getStatus()).isEqualTo(AddressStatus.DELETED);
              assertThat(address.getModifiedAt()).isEqualTo(modifiedAtAfterDelete);
            });
  }

  @Test
  void deleteOfAnUnknownIdReturns404NotFoundProblem() throws Exception {
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, "665f1c2ab3e4d51a2c9d0e77")
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void deleteOfAnAddressOwnedByAnotherOrganisationReturns404() throws Exception {
    Address saved = saveAddress(AddressStatus.ACTIVE);

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", "org-other", saved.getId())
                .header(ORG_HEADER, "org-other"))
        .andExpect(status().isNotFound());

    // the address remains untouched under its owning organisation
    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(address -> assertThat(address.getStatus()).isEqualTo(AddressStatus.ACTIVE));
  }

  @Test
  void putWithoutTheOrgHeaderReturns400BadRequestWithNoErrorsMap() throws Exception {
    Address saved = saveAddress(AddressStatus.ACTIVE);

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
