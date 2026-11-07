package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterStatus;

class TransporterScopingIT extends IntegrationBase {

  private static final String ORG_A = ORGANISATION_ID;
  private static final String ORG_B = OTHER_ORG;

  private String createAsOrgA() throws Exception {
    return createdResourceId(postTransporterAs(ORG_A, ORG_A, transporterBody()));
  }

  @Test
  void orgBBrowsingItsOwnPathCannotSeeOrReachOrgAsTransporter() throws Exception {
    String id = createAsOrgA();

    listTransportersAs(ORG_B, ORG_B)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalItems").value(0));
    getTransporterAs(ORG_B, ORG_B, id).andExpect(status().isNotFound());
    putTransporterAs(ORG_B, ORG_B, id, transporterBody()).andExpect(status().isNotFound());
    deleteTransporterAs(ORG_B, ORG_B, id).andExpect(status().isNotFound());
    getTransporterAs(ORG_A, ORG_A, id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void forwardedOrgHeaderDisagreeingWithThePathOrgIdIs404() throws Exception {
    String id = createAsOrgA();

    getTransporterAs(ORG_A, ORG_B, id).andExpect(status().isNotFound());
    listTransportersAs(ORG_A, ORG_B).andExpect(status().isNotFound());
    putTransporterAs(ORG_A, ORG_B, id, transporterBody()).andExpect(status().isNotFound());
    deleteTransporterAs(ORG_A, ORG_B, id).andExpect(status().isNotFound());
    assertThat(transporterRepository.findByIdAndOrganisationId(id, ORG_A))
        .get()
        .extracting("status")
        .isEqualTo(TransporterStatus.ACTIVE);
    postTransporterAs(ORG_A, ORG_B, transporterBody()).andExpect(status().isNotFound());
    assertThat(activeTransportersFor(ORG_A)).singleElement();
  }

  @Test
  void organisationIdIsStampedOnCreateAndScopesReads() throws Exception {
    String id = createAsOrgA();

    assertThat(transporterRepository.findByIdAndOrganisationId(id, ORG_A))
        .get()
        .satisfies(transporter -> assertThat(transporter.getOrganisationId()).isEqualTo(ORG_A));

    getTransporterAs(ORG_A, ORG_A, id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.organisationId").value(ORG_A));
    listTransportersAs(ORG_A, ORG_A)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }
}
