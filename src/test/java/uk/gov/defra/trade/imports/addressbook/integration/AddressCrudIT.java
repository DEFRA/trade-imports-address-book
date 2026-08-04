package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * Full-stack create integration test for {@code POST /organisation/{orgId}/addresses}. Covers the
 * create leg (201 + {@code Location} + the server-stamped organisationId), the camelCase wire, the
 * derived {@code deleted} boolean tombstone signal, and the 400 validation problem whose {@code errors}
 * map is keyed by the camelCase wire field names.
 */
class AddressCrudIT extends IntegrationBase {

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

  @Autowired private OperatorRepository repository;

  @Test
  void post_shouldReturn201WithLocationAndStampedOrganisation() throws Exception {
    // Given
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

    // When / Then
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    containsString("/organisation/" + ORGANISATION_ID + "/addresses/")))
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

    assertThat(activeAddressesFor(ORGANISATION_ID))
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
  void post_shouldReturn400ValidationProblemKeyedByCamelCaseFieldNames() throws Exception {
    // Given
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

    // When / Then
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
        .andExpect(jsonPath("$.errors.address_line_1").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldReturn400BadRequestProblem_whenJsonBodyIsMalformed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void put_shouldReturn400BadRequestProblem_whenJsonBodyIsEmpty() throws Exception {
    // Given
    Address saved =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(ORGANISATION_ID)
            .status(AddressStatus.ACTIVE)
            .build();
    saved = repository.save(saved);

    // When / Then
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void post_shouldRejectStrayTypeOrRoleFieldsWithPerFieldErrors() throws Exception {
    // Given
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

    // When / Then
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

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldIgnoreEchoedReadOnlyAndUnknownFields() throws Exception {
    // Given
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

    // When / Then
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.id").value(not("echoed-read-only-id")))
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.createdAt").value(not(startsWith("2020"))));

    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(
            address ->
                assertThat(address.getCreatedAt())
                    .isAfter(Instant.parse("2020-01-01T00:00:00Z")));
  }

  @Test
  void post_shouldAcceptCountryCodeAsGivenAndFreeStringPhone() throws Exception {
    // Given
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

    // When / Then
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.countryCode").value("FR"))
        .andExpect(jsonPath("$.phone").value("ring the office"));

    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(address -> assertThat(address.getCountryCode()).isEqualTo("FR"));
  }

  @Test
  void post_shouldReturn400_whenCountryCodeIsBlank() throws Exception {
    // Given
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

    // When / Then
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.errors.countryCode").exists());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void crudRoundTrip_shouldCreateGetPutDeleteAndIdempotentRepeatDelete() throws Exception {
    // Given
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

    // When — create
    String location =
        mockMvc
            .perform(
                post("/organisation/{orgId}/addresses", ORGANISATION_ID)
                    .header(ORG_HEADER, ORGANISATION_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn()
            .getResponse()
            .getHeader("Location");
    assertThat(location).isNotNull();
    String id = location.substring(location.lastIndexOf('/') + 1);

    // Then — get live
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));

    // When — put
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"));

    // When — delete
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());
    assertThat(repository.findByIdAndOrganisationId(id, ORGANISATION_ID)).isPresent();
    Instant modifiedAtAfterDelete =
        repository.findByIdAndOrganisationId(id, ORGANISATION_ID).orElseThrow().getModifiedAt();

    // Then — tombstone still fetchable
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.deleted").value(true));

    // When — put on tombstone
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isNotFound());

    // When — repeat delete
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());

    // Then — idempotent, no state change
    assertThat(repository.findByIdAndOrganisationId(id, ORGANISATION_ID))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getStatus()).isEqualTo(AddressStatus.DELETED);
              assertThat(address.getModifiedAt()).isEqualTo(modifiedAtAfterDelete);
            });
  }
}
