package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.transporter.Transporter;

class TransporterDeleteIT extends IntegrationBase {

  @Autowired private ObjectMapper objectMapper;

  @Test
  void aSoftDeletedTransporterLeavesTheListAndStaysReadableByIdMarkedDeleted() throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));

    deleteTransporter(id).andExpect(status().isNoContent());

    listTransporters().andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    getTransporter(id).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void deletingAnAlreadyDeletedTransporterChangesNothing() throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));

    deleteTransporter(id).andExpect(status().isNoContent());
    String modifiedAtAfterFirstDelete = modifiedAtOf(id);

    deleteTransporter(id).andExpect(status().isNoContent());

    getTransporter(id).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
    assertThat(modifiedAtOf(id)).isEqualTo(modifiedAtAfterFirstDelete);
  }

  @Test
  void delete_shouldReturn404NotFoundProblem_whenIdIsUnknown() throws Exception {
    deleteTransporter(UNKNOWN_ID)
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void delete_shouldPreventTombstoneResurrection_whenStaleSaveFollowsDelete() throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));
    Transporter stale = transporterRepository.findByIdAndOrganisationId(id, ORGANISATION_ID).orElseThrow();

    deleteTransporter(id).andExpect(status().isNoContent());

    stale.setName("Resurrected");

    assertThatThrownBy(() -> transporterRepository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    getTransporter(id).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
  }

  private String modifiedAtOf(String id) throws Exception {
    return objectMapper
        .readTree(
            getTransporter(id)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("modifiedAt")
        .asText();
  }
}
