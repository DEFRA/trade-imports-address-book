package uk.gov.defra.trade.imports.addressbook.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.elastic.logging.JsonUtils;
import co.elastic.logging.logback.EcsEncoder;
import java.util.List;
import org.slf4j.event.KeyValuePair;

/**
 * The ECS encoder, plus a nested {@code event} object built from the line's {@code event.*}
 * key-value pairs. A {@link Number} value is written as a JSON number, and a non-finite one as a
 * string. A line with no {@code event.*} key-value pairs is written exactly as the ECS encoder
 * writes it.
 */
public class EcsEventEncoder extends EcsEncoder {

  private static final String EVENT_PREFIX = "event.";

  @Override
  protected void addCustomFields(ILoggingEvent event, StringBuilder builder) {
    super.addCustomFields(event, builder);
    List<KeyValuePair> eventFields = eventFields(event);
    if (eventFields.isEmpty()) {
      return;
    }
    builder.append("\"event\":{");
    for (KeyValuePair field : eventFields) {
      builder.append('"');
      JsonUtils.quoteAsString(field.key.substring(EVENT_PREFIX.length()), builder);
      builder.append("\":");
      appendValue(field.value, builder);
      builder.append(',');
    }
    builder.setLength(builder.length() - 1);
    builder.append("},");
  }

  private static List<KeyValuePair> eventFields(ILoggingEvent event) {
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    if (pairs == null) {
      return List.of();
    }
    return pairs.stream().filter(pair -> pair.key.startsWith(EVENT_PREFIX)).toList();
  }

  private static void appendValue(Object value, StringBuilder builder) {
    if (value == null) {
      builder.append("null");
    } else if (value instanceof Number number && isFinite(number)) {
      builder.append(number);
    } else {
      builder.append('"');
      JsonUtils.quoteAsString(value.toString(), builder);
      builder.append('"');
    }
  }

  private static boolean isFinite(Number number) {
    return switch (number) {
      case Double d -> Double.isFinite(d);
      case Float f -> Float.isFinite(f);
      default -> true;
    };
  }
}
