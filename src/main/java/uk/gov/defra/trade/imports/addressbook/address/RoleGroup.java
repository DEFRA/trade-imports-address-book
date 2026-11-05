package uk.gov.defra.trade.imports.addressbook.address;

enum RoleGroup {
  SENDING(false),
  RECEIVING(true),
  BRANCH(false);

  private final boolean inTheUnitedKingdom;

  RoleGroup(boolean inTheUnitedKingdom) {
    this.inTheUnitedKingdom = inTheUnitedKingdom;
  }

  boolean inTheUnitedKingdom() {
    return inTheUnitedKingdom;
  }
}
