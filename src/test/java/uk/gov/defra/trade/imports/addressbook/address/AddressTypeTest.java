package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddressTypeTest {

  @Test
  void originOrConsignorListsPlaceOfOriginAndConsignor() {
    assertThat(AddressType.ORIGIN_OR_CONSIGNOR.uses())
        .containsExactly(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR);
  }

  @Test
  void destinationConsigneeOrImporterListsPlaceOfDestinationConsigneeAndImporter() {
    assertThat(AddressType.DESTINATION_CONSIGNEE_OR_IMPORTER.uses())
        .containsExactly(AddressUse.PLACE_OF_DESTINATION, AddressUse.CONSIGNEE, AddressUse.IMPORTER);
  }

  @Test
  void branchListsBranchAddress() {
    assertThat(AddressType.BRANCH.uses()).containsExactly(AddressUse.BRANCH_ADDRESS);
  }

  @Test
  void everyUseIsListedByExactlyOneType() {
    List<AddressUse> listedUses =
        Arrays.stream(AddressType.values()).flatMap(type -> type.uses().stream()).toList();

    assertThat(listedUses).containsExactlyInAnyOrder(AddressUse.values());
  }

  @Test
  void onlyOriginOrConsignorListsAnAddressWithNoUses() {
    assertThat(Arrays.stream(AddressType.values()).filter(AddressType::listsAddressWithNoUses))
        .containsExactly(AddressType.ORIGIN_OR_CONSIGNOR);
  }
}
