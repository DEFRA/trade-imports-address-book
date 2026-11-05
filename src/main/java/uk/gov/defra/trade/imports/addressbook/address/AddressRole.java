package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** A party a notification can choose this address for. */
@Schema(description = "A party a notification can choose this address for")
public enum AddressRole {
  PLACE_OF_ORIGIN(RoleGroup.SENDING),
  CONSIGNOR(RoleGroup.SENDING),
  PLACE_OF_DESTINATION(RoleGroup.RECEIVING),
  CONSIGNEE(RoleGroup.RECEIVING),
  IMPORTER(RoleGroup.RECEIVING),
  BRANCH_ADDRESS(RoleGroup.BRANCH);

  private final RoleGroup group;

  AddressRole(RoleGroup group) {
    this.group = group;
  }

  RoleGroup group() {
    return group;
  }

  static List<AddressRole> rolesNamed(Collection<String> names) {
    return Arrays.stream(values()).filter(role -> names.contains(role.name())).toList();
  }
}
