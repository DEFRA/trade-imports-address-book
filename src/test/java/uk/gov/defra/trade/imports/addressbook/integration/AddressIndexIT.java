package uk.gov.defra.trade.imports.addressbook.integration;

import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

class AddressIndexIT extends IntegrationBase {

  @Test
  void index_shouldDeclareOnlyOrgStatusCreatedOnOrganisationStatusAndCreatedAt() {
    assertDeclaresOnlyOrgStatusCreatedIndex(Address.class);
  }

  @Test
  void listQuery_shouldUseOrgStatusCreatedIndex() {
    assertListQueryUsesOrgStatusCreatedIndex("addresses", AddressStatus.ACTIVE.name());
  }
}
