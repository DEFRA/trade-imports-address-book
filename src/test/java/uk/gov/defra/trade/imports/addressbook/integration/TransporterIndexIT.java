package uk.gov.defra.trade.imports.addressbook.integration;

import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.transporter.Transporter;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterStatus;

class TransporterIndexIT extends IntegrationBase {

  @Test
  void index_shouldDeclareOnlyOrgStatusCreatedOnOrganisationStatusAndCreatedAt() {
    assertDeclaresOnlyOrgStatusCreatedIndex(Transporter.class);
  }

  @Test
  void listQuery_shouldUseOrgStatusCreatedIndex() {
    assertListQueryUsesOrgStatusCreatedIndex("transporters", TransporterStatus.ACTIVE.name());
  }
}
