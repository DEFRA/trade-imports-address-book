package uk.gov.defra.trade.imports.addressbook.metrics;

import java.util.ArrayList;
import java.util.List;

/** Values recorded by a meter, kept until they are taken. Safe for concurrent use. */
public final class RecordedValues {

  private List<Double> values = new ArrayList<>();

  synchronized void add(double value) {
    values.add(value);
  }

  /**
   * Takes every value recorded since the last take, in the order recorded.
   *
   * @return the values; empty when none were recorded
   */
  public synchronized List<Double> takeAll() {
    List<Double> taken = values;
    values = new ArrayList<>();
    return taken;
  }
}
