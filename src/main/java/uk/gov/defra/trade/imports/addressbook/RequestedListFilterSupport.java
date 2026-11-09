package uk.gov.defra.trade.imports.addressbook;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** What every requested-filter record shares: a null-safe stream of a repeatable parameter's present values. */
public final class RequestedListFilterSupport {

  private RequestedListFilterSupport() {}

  /**
   * @param values a repeatable request parameter's values, possibly null, possibly holding nulls
   * @return the non-null values, in a stream; empty if {@code values} is null
   */
  public static <T> Stream<T> presentElements(List<T> values) {
    return values == null ? Stream.empty() : values.stream().filter(Objects::nonNull);
  }
}
