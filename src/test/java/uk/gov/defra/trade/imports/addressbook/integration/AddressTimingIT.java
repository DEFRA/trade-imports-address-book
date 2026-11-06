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

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void eachOperationAddsOneCallToOnlyItsOwnControllerAndServiceTimers() throws Exception {
    Map<String, Long> beforeCreate = timerCounts();
    String addressId = createdAddressId(postAddress(addressBody()));
    assertOnlyTimersOfOperationMovedByOne("create", beforeCreate);

    Map<String, Long> beforeList = timerCounts();
    listAddresses().andExpect(status().isOk());
    assertOnlyTimersOfOperationMovedByOne("list", beforeList);

    Map<String, Long> beforeGet = timerCounts();
    getAddress(addressId).andExpect(status().isOk());
    assertOnlyTimersOfOperationMovedByOne("get", beforeGet);

    Map<String, Long> beforeUpdate = timerCounts();
    putAddress(addressId, addressBody().with("name", "Renamed Ltd")).andExpect(status().isOk());
    assertOnlyTimersOfOperationMovedByOne("update", beforeUpdate);

    Map<String, Long> beforeDelete = timerCounts();
    deleteAddress(addressId).andExpect(status().isNoContent());
    assertOnlyTimersOfOperationMovedByOne("delete", beforeDelete);
  }

  private void assertOnlyTimersOfOperationMovedByOne(String operation, Map<String, Long> before) {
    Map<String, Long> moved = new TreeMap<>();
    timerCounts().forEach((name, count) -> moved.put(name, count - before.get(name)));
    Map<String, Long> expected = new TreeMap<>();
    before.keySet().forEach(name -> expected.put(name, 0L));
    expected.put("controller." + operation + ".time", 1L);
    expected.put("service." + operation + ".time", 1L);
    assertThat(moved).as(operation).isEqualTo(expected);
  }

  private Map<String, Long> timerCounts() {
    Map<String, Long> counts = new TreeMap<>();
    for (String operation : List.of("list", "create", "get", "update", "delete")) {
      for (String layer : List.of("controller", "service")) {
        String name = layer + "." + operation + ".time";
        counts.put(name, meterRegistry.find(name).timers().stream().mapToLong(Timer::count).sum());
      }
    }
    return counts;
  }
}
