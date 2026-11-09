package uk.gov.defra.trade.imports.addressbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

class ListMatchCriteriaTest {

  @Test
  void nonEmptyCopyOf_refusesAnEmptyList() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> ListMatchCriteria.nonEmptyCopyOf(List.of()))
        .withMessage("A match needs at least one value");
  }

  @Test
  void nonEmptyCopyOf_copiesANonEmptyListDefensively() {
    List<String> values = List.of("a", "b");

    assertThat(ListMatchCriteria.nonEmptyCopyOf(values)).containsExactly("a", "b");
  }

  @Test
  void textMatching_matchesAnyOfTheSearchableFields() {
    Criteria criteria = ListMatchCriteria.textMatching(List.of("name", "postcode"), "drover");

    assertThat(criteria.getCriteriaObject().toJson()).contains("$or").contains("name").contains(
        "postcode");
  }

  @Test
  void inCountries_matchesAnyOfTheCountryCodes() {
    Criteria criteria = ListMatchCriteria.inCountries(List.of("GB", "FR"));

    assertThat(criteria.getCriteriaObject().toJson()).contains("countryCode");
  }
}
