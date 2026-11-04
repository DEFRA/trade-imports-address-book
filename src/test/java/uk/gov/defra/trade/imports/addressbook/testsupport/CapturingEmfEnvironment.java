package uk.gov.defra.trade.imports.addressbook.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.cloudwatchlogs.emf.environment.Environment;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.MetricsContext;
import software.amazon.cloudwatchlogs.emf.sinks.ISink;

public class CapturingEmfEnvironment implements Environment {

  public static final String NAMESPACE = "trade-imports-address-book";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<String> serialised = new ArrayList<>();

  public MetricsLogger metricsLogger() {
    return new MetricsLogger(this);
  }

  public List<JsonNode> documents() {
    return serialised.stream().map(CapturingEmfEnvironment::parse).toList();
  }

  private static JsonNode parse(String document) {
    try {
      return OBJECT_MAPPER.readTree(document);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean probe() {
    return true;
  }

  @Override
  public String getName() {
    return "test";
  }

  @Override
  public String getType() {
    return "test";
  }

  @Override
  public String getLogGroupName() {
    return "test";
  }

  @Override
  public void configureContext(MetricsContext context) {
    // Adds no environment properties to the context.
  }

  @Override
  public ISink getSink() {
    return new ISink() {
      @Override
      public void accept(MetricsContext context) {
        try {
          serialised.addAll(context.serialize());
        } catch (JsonProcessingException e) {
          throw new IllegalStateException(e);
        }
      }

      @Override
      public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
      }
    };
  }
}
