package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRequest;
import uk.gov.defra.trade.imports.addressbook.address.OperatorService;

class OperatorServiceTimingIT extends IntegrationBase {

  @Autowired private OperatorService operatorService;

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void eachServiceOperation_shouldTimeOneCallOnItsOwnTimer() {
    // Given
    long listedBefore = timedCalls("service.listOperators.time");
    long createdBefore = timedCalls("service.createOperator.time");
    long gotBefore = timedCalls("service.getOperator.time");
    long updatedBefore = timedCalls("service.updateOperator.time");
    long deletedBefore = timedCalls("service.deleteOperator.time");

    // When
    Address created = operatorService.create(request(), ORGANISATION_ID);
    operatorService.list(ORGANISATION_ID, 1, null, null, null);
    operatorService.get(created.getId(), ORGANISATION_ID);
    operatorService.update(created.getId(), request(), ORGANISATION_ID);
    operatorService.delete(created.getId(), ORGANISATION_ID);

    // Then
    assertThat(timedCalls("service.listOperators.time")).isEqualTo(listedBefore + 1);
    assertThat(timedCalls("service.createOperator.time")).isEqualTo(createdBefore + 1);
    assertThat(timedCalls("service.getOperator.time")).isEqualTo(gotBefore + 1);
    assertThat(timedCalls("service.updateOperator.time")).isEqualTo(updatedBefore + 1);
    assertThat(timedCalls("service.deleteOperator.time")).isEqualTo(deletedBefore + 1);
  }

  private long timedCalls(String timerName) {
    return meterRegistry.find(timerName).timers().stream().mapToLong(Timer::count).sum();
  }

  private static AddressRequest request() {
    return AddressRequest.builder()
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .townOrCity("Inverness")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .build();
  }
}
