package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * EUDPA-286 m1-02 — PUT /organisation/{orgId}/addresses/{id} full replace. Omitted optionals are
 * cleared (not PATCH semantics). Same validation as create; tombstones and unknown/cross-org ids
 * return 404.
 */
class AddressUpdateIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String UNKNOWN_ID = "665f1c2ab3e4d51a2c9d0e77";

  private static final String VALID_REPLACE_BODY =
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

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private Address saveActiveWithOptionals() {
    Address address =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .addressLine2("Unit 3")
            .townOrCity("Inverness")
            .county("Highland")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(ORG)
            .status(AddressStatus.ACTIVE)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(address);
  }

  @Test
  void putReplacesAllFieldsAndClearsOmittedOptionals() throws Exception {
    Address saved = saveActiveWithOptionals();

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG, saved.getId())
                .header(ORG_HEADER, ORG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"))
        .andExpect(jsonPath("$.addressLine1").value("2 Market Street"))
        .andExpect(jsonPath("$.townOrCity").value("Perth"))
        .andExpect(jsonPath("$.countryCode").value("IE"))
        .andExpect(jsonPath("$.deleted").value(false));

    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getAddressLine2()).isNull();
              assertThat(address.getCounty()).isNull();
              assertThat(address.getName()).isEqualTo("Lowland Cattle Co");
            });
  }

  @Test
  void putWithInvalidFieldsReturns400PerFieldErrorsMap() throws Exception {
    Address saved = saveActiveWithOptionals();
    String body =
        """
        {
          "name": "Lowland Cattle Co",
          "addressLine1": "",
          "townOrCity": "Perth",
          "postcode": "PH1 5AA",
          "countryCode": "IE",
          "phone": "+44 1738 111222",
          "email": "not-an-email"
        }
        """;

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG, saved.getId())
                .header(ORG_HEADER, ORG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.email").exists());
  }

  @Test
  void putUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG, UNKNOWN_ID)
                .header(ORG_HEADER, ORG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"));
  }

  @Test
  void putOnSoftDeletedTombstoneReturns404() throws Exception {
    Address tombstone =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(ORG)
            .status(AddressStatus.DELETED)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    tombstone = repository.save(tombstone);

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG, tombstone.getId())
                .header(ORG_HEADER, ORG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isNotFound());
  }

  @Test
  void putBumpsModifiedAtAndPreservesCreatedAt() throws Exception {
    Address saved = saveActiveWithOptionals();
    Instant createdAt = saved.getCreatedAt();
    Instant baselineModifiedAt = saved.getModifiedAt();

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG, saved.getId())
                .header(ORG_HEADER, ORG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isOk());

    assertThat(repository.findById(saved.getId()))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getCreatedAt()).isEqualTo(createdAt);
              assertThat(address.getModifiedAt()).isAfter(baselineModifiedAt);
            });
  }
}
