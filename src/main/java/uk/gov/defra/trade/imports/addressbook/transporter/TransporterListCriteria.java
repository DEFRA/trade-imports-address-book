package uk.gov.defra.trade.imports.addressbook.transporter;

import java.util.List;
import java.util.Objects;

/**
 * What a transporter list query keeps and searches for. A filter or match the caller did not ask
 * for is absent.
 *
 * @param types keeps only transporters of any of these types; empty keeps every type
 * @param matches the search: a transporter matches when it matches any of these; empty for no
 *     search
 */
public record TransporterListCriteria(List<TransporterType> types, List<Match> matches) {

  public TransporterListCriteria {
    types = List.copyOf(types);
    matches = List.copyOf(matches);
  }

  /** One way a transporter can match the search. */
  public sealed interface Match permits TextMatching, InCountries, OfType {}

  /**
   * Matches a transporter whose searchable text matches the pattern.
   *
   * @param pattern a regular expression
   */
  public record TextMatching(String pattern) implements Match {
    public TextMatching {
      Objects.requireNonNull(pattern);
    }
  }

  /**
   * Matches a transporter in any of the countries.
   *
   * @param countryCodes the country codes; never empty
   */
  public record InCountries(List<String> countryCodes) implements Match {
    public InCountries {
      countryCodes = nonEmptyCopyOf(countryCodes);
    }
  }

  /**
   * Matches a transporter of any of these types.
   *
   * @param types the types to match; never empty
   */
  public record OfType(List<TransporterType> types) implements Match {
    public OfType {
      types = nonEmptyCopyOf(types);
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
