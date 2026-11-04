package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRequest;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorService;

class OperatorServiceTimingIT extends IntegrationBase {

  @Autowired private OperatorService operatorService;

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void create_shouldTimeOneCallOnTheCreateTimer() {
    long before = timedCalls("service.createOperator.time");

    operatorService.create(request(), ORGANISATION_ID);

    assertThat(timedCalls("service.createOperator.time")).isEqualTo(before + 1);
  }

  @Test
  void list_shouldTimeOneCallOnTheListTimer() {
    long before = timedCalls("service.listOperators.time");

    operatorService.list(ORGANISATION_ID, 1, null, null, null);

    assertThat(timedCalls("service.listOperators.time")).isEqualTo(before + 1);
  }

  @Test
  void get_shouldTimeOneCallOnTheGetTimer() {
    Address stored = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE));
    long before = timedCalls("service.getOperator.time");

    operatorService.get(stored.getId(), ORGANISATION_ID);

    assertThat(timedCalls("service.getOperator.time")).isEqualTo(before + 1);
  }

  @Test
  void update_shouldTimeOneCallOnTheUpdateTimer() {
    Address stored = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE));
    long before = timedCalls("service.updateOperator.time");

    operatorService.update(stored.getId(), request(), ORGANISATION_ID);

    assertThat(timedCalls("service.updateOperator.time")).isEqualTo(before + 1);
  }

  @Test
  void delete_shouldTimeOneCallOnTheDeleteTimer() {
    Address stored = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE));
    long before = timedCalls("service.deleteOperator.time");

    operatorService.delete(stored.getId(), ORGANISATION_ID);

    assertThat(timedCalls("service.deleteOperator.time")).isEqualTo(before + 1);
  }

  private long timedCalls(String timerName) {
    return meterRegistry.find(timerName).timers().stream().mapToLong(Timer::count).sum();
  }

  private static AddressRequest request() {
    Address address = addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).build();
    return AddressRequest.builder()
        .name(address.getName())
        .addressLine1(address.getAddressLine1())
        .townOrCity(address.getTownOrCity())
        .postcode(address.getPostcode())
        .countryCode(address.getCountryCode())
        .phone(address.getPhone())
        .email(address.getEmail())
        .build();
  }
}
