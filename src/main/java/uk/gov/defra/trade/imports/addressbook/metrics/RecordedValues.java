package uk.gov.defra.trade.imports.addressbook.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

class RecordedValues {

  private final Queue<Double> values = new ConcurrentLinkedQueue<>();

  void add(double value) {
    values.add(value);
  }

  List<Double> takeAll() {
    List<Double> taken = new ArrayList<>();
    Double value;
    while ((value = values.poll()) != null) {
      taken.add(value);
    }
    return taken;
  }
}
