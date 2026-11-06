package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import java.util.Objects;

/**
 * What a list query keeps and searches for. A filter or match the caller did not ask for is absent.
 *
 * @param roles keeps only addresses holding any of these roles; empty keeps every address
 * @param matches the search: an address matches when it matches any of these; empty for no search
 */
public record ListCriteria(List<AddressRole> roles, List<Match> matches) {

  public ListCriteria {
    roles = List.copyOf(roles);
    matches = List.copyOf(matches);
  }

  /** One way an address can match the search. */
  public sealed interface Match permits TextMatching, InCountries, HoldingAnyRole {}

  /**
   * Matches an address whose searchable text matches the pattern.
   *
   * @param pattern a regular expression
   */
  public record TextMatching(String pattern) implements Match {
    public TextMatching {
      Objects.requireNonNull(pattern);
    }
  }

  /**
   * Matches an address in any of the countries.
   *
   * @param countryCodes the country codes; never empty
   */
  public record InCountries(List<String> countryCodes) implements Match {
    public InCountries {
      countryCodes = nonEmptyCopyOf(countryCodes);
    }
  }

  /**
   * Matches an address holding any of the roles.
   *
   * @param roles the roles; never empty
   */
  public record HoldingAnyRole(List<AddressRole> roles) implements Match {
    public HoldingAnyRole {
      roles = nonEmptyCopyOf(roles);
    }
  }

  private static <T> List<T> nonEmptyCopyOf(List<T> values) {
    List<T> copy = List.copyOf(values);
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("A match needs at least one value");
    }
    return copy;
  }
}
