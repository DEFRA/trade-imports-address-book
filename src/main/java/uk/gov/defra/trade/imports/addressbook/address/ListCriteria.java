package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import java.util.Objects;

/**
 * What a list query keeps and searches for. A filter or search term the caller did not ask for is
 * absent.
 *
 * @param roles keeps only addresses holding any of these roles; empty keeps every address
 * @param searchTerms the search: an address matches when it matches any of these; empty for no
 *     search
 */
public record ListCriteria(List<AddressRole> roles, List<SearchTerm> searchTerms) {

  public ListCriteria {
    roles = List.copyOf(roles);
    searchTerms = List.copyOf(searchTerms);
  }

  /** One way an address can match the search. */
  public sealed interface SearchTerm permits TextMatching, InCountries, HoldingAnyRole {}

  /**
   * Matches an address whose name, address lines, town or city, county or postcode matches the
   * pattern, case-insensitively.
   *
   * @param pattern a regular expression
   */
  public record TextMatching(String pattern) implements SearchTerm {
    public TextMatching {
      Objects.requireNonNull(pattern);
    }
  }

  /**
   * Matches an address in any of the countries.
   *
   * @param countryCodes trimmed, non-blank country codes; never empty
   */
  public record InCountries(List<String> countryCodes) implements SearchTerm {
    public InCountries {
      countryCodes = nonEmptyCopyOf(countryCodes);
    }
  }

  /**
   * Matches an address holding any of the roles.
   *
   * @param roles the roles; never empty
   */
  public record HoldingAnyRole(List<AddressRole> roles) implements SearchTerm {
    public HoldingAnyRole {
      roles = nonEmptyCopyOf(roles);
    }
  }

  private static <T> List<T> nonEmptyCopyOf(List<T> values) {
    List<T> copy = List.copyOf(values);
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("A search term needs at least one value");
    }
    return copy;
  }
}
