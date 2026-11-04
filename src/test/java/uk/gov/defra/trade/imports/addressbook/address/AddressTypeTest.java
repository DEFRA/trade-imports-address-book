package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddressTypeTest {

  @Test
  void uses_listsPlaceOfOriginAndConsignorForOriginOrConsignor() {
    assertThat(AddressType.ORIGIN_OR_CONSIGNOR.uses())
        .containsExactly(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR);
  }

  @Test
  void uses_listsPlaceOfDestinationConsigneeAndImporterForDestinationConsigneeOrImporter() {
    assertThat(AddressType.DESTINATION_CONSIGNEE_OR_IMPORTER.uses())
        .containsExactly(AddressUse.PLACE_OF_DESTINATION, AddressUse.CONSIGNEE, AddressUse.IMPORTER);
  }

  @Test
  void uses_listsBranchAddressForBranch() {
    assertThat(AddressType.BRANCH.uses()).containsExactly(AddressUse.BRANCH_ADDRESS);
  }

  @Test
  void uses_listEveryUseUnderExactlyOneType() {
    List<AddressUse> listedUses =
        Arrays.stream(AddressType.values()).flatMap(type -> type.uses().stream()).toList();

    assertThat(listedUses).containsExactlyInAnyOrder(AddressUse.values());
  }

  @Test
  void listsAddressWithNoUses_isTrueOnlyForOriginOrConsignor() {
    assertThat(Arrays.stream(AddressType.values()).filter(AddressType::listsAddressWithNoUses))
        .containsExactly(AddressType.ORIGIN_OR_CONSIGNOR);
  }
}
