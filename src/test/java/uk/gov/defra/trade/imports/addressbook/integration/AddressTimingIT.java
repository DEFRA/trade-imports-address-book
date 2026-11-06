package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AddressTimingIT extends IntegrationBase {

  private static final List<String> OPERATIONS = List.of("list", "create", "get", "update", "delete");

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void eachEndpointAndServiceMethodAddsOneCallToItsOwnTimer() throws Exception {
    Map<String, Long> before = timerCounts();

    String addressId = createdAddressId(postAddress(addressBody()));
    listAddresses().andExpect(status().isOk());
    getAddress(addressId).andExpect(status().isOk());
    putAddress(addressId, addressBody().with("name", "Renamed Ltd")).andExpect(status().isOk());
    deleteAddress(addressId).andExpect(status().isNoContent());

    Map<String, Long> after = timerCounts();
    after.forEach(
        (name, count) -> assertThat(count - before.get(name)).as(name).isEqualTo(1));
  }

  private Map<String, Long> timerCounts() {
    Map<String, Long> counts = new TreeMap<>();
    for (String operation : OPERATIONS) {
      for (String layer : List.of("controller", "service")) {
        String name = layer + "." + operation + ".time";
        counts.put(name, meterRegistry.find(name).timers().stream().mapToLong(Timer::count).sum());
      }
    }
    return counts;
  }
}
