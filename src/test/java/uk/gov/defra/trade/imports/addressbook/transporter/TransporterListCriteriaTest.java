package uk.gov.defra.trade.imports.addressbook.transporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransporterListCriteriaTest {

  @Test
  void inCountries_refusesNoCountryCodes() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new TransporterListCriteria.InCountries(List.of()))
        .withMessage("A match needs at least one value");
  }

  @Test
  void ofType_refusesNoTypes() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new TransporterListCriteria.OfType(List.of()))
        .withMessage("A match needs at least one value");
  }

  @Test
  void transporterListCriteria_copiesTypesDefensively() {
    List<TransporterType> types = new ArrayList<>(List.of(TransporterType.PRIVATE));
    TransporterListCriteria criteria = new TransporterListCriteria(types, List.of());

    types.add(TransporterType.COMMERCIAL);

    assertThat(criteria.types()).containsExactly(TransporterType.PRIVATE);
  }

  @Test
  void transporterListCriteria_copiesMatchesDefensively() {
    List<TransporterListCriteria.Match> matches =
        new ArrayList<>(List.of(new TransporterListCriteria.TextMatching(".*")));
    TransporterListCriteria criteria = new TransporterListCriteria(List.of(), matches);

    matches.add(new TransporterListCriteria.InCountries(List.of("GB")));

    assertThat(criteria.matches()).hasSize(1);
  }
}
