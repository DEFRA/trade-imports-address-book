package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturingEmfEnvironment.NAMESPACE;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import software.amazon.cloudwatchlogs.emf.exception.InvalidNamespaceException;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturingEmfEnvironment;

class EmfMeterRegistryTest {

  private final MockClock clock = new MockClock();
  private final CapturingEmfEnvironment environment = new CapturingEmfEnvironment();
  private final EmfMeterRegistry registry =
      new EmfMeterRegistry(NAMESPACE, clock, environment::metricsLogger);

  @Test
  void publish_shouldWriteEachTimedDurationInMilliseconds_withTheTagsAsTheOnlyDimensions() {
    // Given
    Timer timer =
        registry.timer("ExternalCallDuration", "Dependency", "mongodb", "Operation", "find");
    timer.record(Duration.ofMillis(12));
    timer.record(Duration.ofMillis(250));

    // When
    registry.publish();

    // Then
    JsonNode document = environment.documents().getFirst();
    JsonNode directive = document.get("_aws").get("CloudWatchMetrics").get(0);
    assertThat(directive.get("Namespace").asText()).isEqualTo(NAMESPACE);
    assertThat(directive.get("Dimensions")).hasSize(1);
    assertThat(texts(directive.get("Dimensions").get(0))).containsExactly("Dependency", "Operation");
    assertThat(directive.get("Metrics").get(0).get("Name").asText())
        .isEqualTo("ExternalCallDuration");
    assertThat(directive.get("Metrics").get(0).get("Unit").asText()).isEqualTo("Milliseconds");
    assertThat(document.get("Dependency").asText()).isEqualTo("mongodb");
    assertThat(document.get("Operation").asText()).isEqualTo("find");
    assertThat(numbers(document.get("ExternalCallDuration"))).containsExactly(12.0, 250.0);
    assertThat(document.has("ServiceName")).isFalse();
  }

  @Test
  void publish_shouldWriteEachDurationOnlyOnce() {
    // Given
    registry.timer("controller.listOperators.time").record(Duration.ofMillis(5));
    registry.publish();

    // When
    registry.publish();

    // Then
    assertThat(environment.documents()).hasSize(1);
  }

  @Test
  void publish_shouldWriteEachAmountASummaryRecorded() {
    // Given
    DistributionSummary failures =
        DistributionSummary.builder("ExternalCallFailure")
            .tags("Dependency", "mongodb", "Operation", "find")
            .register(registry);
    failures.record(0);
    failures.record(1);
    failures.record(0);

    // When
    registry.publish();

    // Then
    JsonNode document = environment.documents().getFirst();
    assertThat(numbers(document.get("ExternalCallFailure"))).containsExactly(0.0, 1.0, 0.0);
    assertThat(unitOf(document)).isEqualTo("Count");
  }

  @Test
  void publish_shouldWriteACountersCountForTheLastMinute() {
    // Given
    registry.counter("AddressChange", "Action", "create").increment(3);
    clock.add(Duration.ofMinutes(1));

    // When
    registry.publish();

    // Then
    JsonNode document = environment.documents().getFirst();
    assertThat(document.get("Action").asText()).isEqualTo("create");
    assertThat(document.get("AddressChange").asDouble()).isEqualTo(3.0);
  }

  @Test
  void publish_shouldWriteAGaugesValue_andNothingWhileItIsNotANumber() {
    // Given
    AtomicReference<Double> available = new AtomicReference<>(Double.NaN);
    Gauge.builder("DependencyAvailable", available, AtomicReference::get)
        .tag("Dependency", "mongodb")
        .register(registry);
    registry.publish();
    available.set(1.0);

    // When
    registry.publish();

    // Then
    assertThat(environment.documents()).singleElement()
        .satisfies(document -> assertThat(document.get("DependencyAvailable").asDouble()).isEqualTo(1.0));
  }

  @Test
  void publish_shouldWriteAMeterWithNoTags_withNoDimensions() {
    // Given
    registry.timer("controller.listOperators.query.time").record(Duration.ofMillis(3));

    // When
    registry.publish();

    // Then
    JsonNode directive =
        environment.documents().getFirst().get("_aws").get("CloudWatchMetrics").get(0);
    assertThat(directive.get("Dimensions").get(0)).isEmpty();
  }

  @Test
  void publish_shouldSplitMoreThanAHundredDurationsAcrossDocuments() {
    // Given
    Timer timer = registry.timer("controller.listOperators.time");
    for (int i = 0; i < 150; i++) {
      timer.record(Duration.ofMillis(i));
    }

    // When
    registry.publish();

    // Then
    assertThat(environment.documents())
        .extracting(document -> document.get("controller.listOperators.time").size())
        .containsExactly(100, 50);
  }

  @Test
  void publish_shouldLogAWarningAndCarryOn_whenOneMeterCannotBeWritten() {
    // Given
    registry.counter("AddressChange", "Action", " ").increment();
    registry.counter("AddressSearch", "SearchType", "none").increment();
    clock.add(Duration.ofMinutes(1));

    // When
    var logs = CapturedLogs.loggedBy(EmfMeterRegistry.class, registry::publish);

    // Then
    assertThat(logs)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .isEqualTo("Could not publish metric AddressChange");
            });
    assertThat(environment.documents())
        .extracting(document -> document.get("SearchType").asText())
        .containsExactly("none");
  }

  @Test
  void constructor_shouldRejectABlankNamespace() {
    assertThatThrownBy(() -> new EmfMeterRegistry(" ", clock, environment::metricsLogger))
        .isInstanceOf(InvalidNamespaceException.class);
  }

  private static String unitOf(JsonNode document) {
    return document.get("_aws").get("CloudWatchMetrics").get(0).get("Metrics").get(0)
        .get("Unit").asText();
  }

  private static List<String> texts(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
  }

  private static List<Double> numbers(JsonNode value) {
    if (!value.isArray()) {
      return List.of(value.asDouble());
    }
    return StreamSupport.stream(value.spliterator(), false).map(JsonNode::asDouble).toList();
  }
}
