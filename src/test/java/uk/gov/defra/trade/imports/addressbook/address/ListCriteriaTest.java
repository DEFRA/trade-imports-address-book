package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListCriteriaTest {

  @Test
  void refusesANullCountryCodeList() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria(null, null, null, null, List.of()));
  }

  @Test
  void refusesANullSearchUseList() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new ListCriteria(null, null, null, List.of(), null));
  }
}
