package uk.gov.defra.trade.imports.addressbook;

import java.util.regex.Pattern;

/** The search-term-to-pattern rule every address-book list search shares. */
public final class SearchTerms {

  private SearchTerms() {}

  /**
   * @param searchTerm the user's search term
   * @return a regular expression matching any text containing {@code searchTerm}
   */
  public static String toPartialMatchRegex(String searchTerm) {
    return ".*" + Pattern.quote(searchTerm) + ".*";
  }
}
