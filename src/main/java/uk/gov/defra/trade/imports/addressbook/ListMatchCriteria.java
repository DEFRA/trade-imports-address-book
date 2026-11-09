package uk.gov.defra.trade.imports.addressbook;

import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * The Mongo {@link Criteria} every address-book list search shares: a partial-word match over a
 * document's own searchable fields, and a match on country code.
 */
public final class ListMatchCriteria {

  private ListMatchCriteria() {}

  /**
   * @param searchableFields the document's own field names a text search matches against
   * @param pattern the partial-match regular expression
   * @return a clause matching any of the fields against the pattern, case-insensitively
   */
  public static Criteria textMatching(List<String> searchableFields, String pattern) {
    return new Criteria()
        .orOperator(searchableFields.stream().map(field -> Criteria.where(field).regex(pattern, "i")).toList());
  }

  /**
   * @param countryCodes the country codes to match
   * @return a clause matching any of the country codes
   */
  public static Criteria inCountries(List<String> countryCodes) {
    return Criteria.where("countryCode").in(countryCodes);
  }

  /**
   * @param values the match's own values
   * @return an unmodifiable copy of {@code values}
   * @throws IllegalArgumentException if {@code values} is empty
   */
  public static <T> List<T> nonEmptyCopyOf(List<T> values) {
    List<T> copy = List.copyOf(values);
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("A match needs at least one value");
    }
    return copy;
  }
}
