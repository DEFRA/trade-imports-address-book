package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListCriteriaTest {

  @Test
  void listCriteria_refusesANullRoleList() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria(null, List.of()));
  }

  @Test
  void listCriteria_refusesANullSearchTermList() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria(List.of(), null));
  }

  @Test
  void textMatching_refusesANullPattern() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria.TextMatching(null));
  }

  @Test
  void inCountries_refusesNoCountryCodes() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new ListCriteria.InCountries(List.of()))
        .withMessage("A search term needs at least one value");
  }

  @Test
  void holdingAnyRole_refusesNoRoles() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new ListCriteria.HoldingAnyRole(List.of()))
        .withMessage("A search term needs at least one value");
  }
}
