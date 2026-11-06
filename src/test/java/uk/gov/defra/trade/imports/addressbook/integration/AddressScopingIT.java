package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

class AddressScopingIT extends IntegrationBase {

  private static final String ORG_A = ORGANISATION_ID;
  private static final String ORG_B = OTHER_ORG;

  @Autowired private ObjectMapper objectMapper;

  private String createAsOrgA() throws Exception {
    return createdAddressId(postAddressAs(ORG_A, ORG_A, addressBody()));
  }

  @Test
  void orgBBrowsingItsOwnPathCannotSeeOrReachOrgAsAddress() throws Exception {
    String id = createAsOrgA();

    listAddressesAs(ORG_B, ORG_B)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalItems").value(0));
    getAddressAs(ORG_B, ORG_B, id).andExpect(status().isNotFound());
    putAddressAs(ORG_B, ORG_B, id, addressBody()).andExpect(status().isNotFound());
    deleteAddressAs(ORG_B, ORG_B, id).andExpect(status().isNotFound());
    getAddressAs(ORG_A, ORG_A, id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void forwardedOrgHeaderDisagreeingWithThePathOrgIdIs404() throws Exception {
    String id = createAsOrgA();

    getAddressAs(ORG_A, ORG_B, id).andExpect(status().isNotFound());
    listAddressesAs(ORG_A, ORG_B).andExpect(status().isNotFound());
    putAddressAs(ORG_A, ORG_B, id, addressBody()).andExpect(status().isNotFound());
    deleteAddressAs(ORG_A, ORG_B, id).andExpect(status().isNotFound());
    assertThat(addressRepository.findByIdAndOrganisationId(id, ORG_A))
        .get()
        .extracting("status")
        .isEqualTo(AddressStatus.ACTIVE);
    postAddressAs(ORG_A, ORG_B, addressBody()).andExpect(status().isNotFound());
    assertThat(activeAddressesFor(ORG_A)).singleElement();
  }

  @Test
  void missingOrgHeaderIsAPlatformAuthRejection400WithNoErrorsMap() throws Exception {
    listAddressesAs(ORG_A, null).andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void blankOrgHeaderIsAPlatformAuthRejection400WithNoErrorsMap() throws Exception {
    listAddressesAs(ORG_A, "   ").andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void missingOrgHeaderOnPostIsAPlatformAuthRejection400AndPersistsNothing() throws Exception {
    postAddressAs(ORG_A, null, addressBody()).andExpect(badRequestProblemWithNoErrors());

    assertThat(storedAddressCount()).isZero();
  }

  @Test
  void organisationIdIsStampedOnCreateAndScopesReads() throws Exception {
    String id = createAsOrgA();

    assertThat(addressRepository.findByIdAndOrganisationId(id, ORG_A))
        .get()
        .satisfies(address -> assertThat(address.getOrganisationId()).isEqualTo(ORG_A));

    getAddressAs(ORG_A, ORG_A, id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.organisationId").value(ORG_A));
    listAddressesAs(ORG_A, ORG_A)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void a404ForAnotherOrgsLiveAddressHasTheSameProblemBodyAsA404ForAnUnknownId() throws Exception {
    String liveId = createAsOrgA();

    MvcResult crossOrg =
        getAddressAs(ORG_B, ORG_B, liveId).andExpect(status().isNotFound()).andReturn();
    MvcResult unknown =
        getAddressAs(ORG_B, ORG_B, UNKNOWN_ID).andExpect(status().isNotFound()).andReturn();
    assertThat(problemBody(crossOrg)).isEqualTo(problemBody(unknown));
    assertThat(problemBody(crossOrg)).doesNotContainKey("instance");
    assertThat(problemBody(unknown)).doesNotContainKey("instance");
  }

  private Map<String, Object> problemBody(MvcResult result) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return body;
  }
}
