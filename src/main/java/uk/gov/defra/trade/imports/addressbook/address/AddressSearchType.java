package uk.gov.defra.trade.imports.addressbook.address;

/** Which filters a list of an organisation's addresses is narrowed by. */
public enum AddressSearchType {
  NONE("none"),
  QUERY("query"),
  COUNTRY("country"),
  QUERY_AND_COUNTRY("query-and-country");

  private final String metricValue;

  AddressSearchType(String metricValue) {
    this.metricValue = metricValue;
  }

  /**
   * This search type as a lower-case, hyphenated metric value.
   *
   * @return the metric value, for example {@code query-and-country}
   */
  public String metricValue() {
    return metricValue;
  }
}
