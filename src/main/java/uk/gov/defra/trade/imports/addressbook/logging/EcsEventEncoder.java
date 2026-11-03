package uk.gov.defra.trade.imports.addressbook.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.elastic.logging.JsonUtils;
import co.elastic.logging.logback.EcsEncoder;
import java.util.List;
import org.slf4j.event.KeyValuePair;

/**
 * The ECS encoder, plus a nested {@code event} object built from the line's {@code event.*}
 * key-value pairs.
 *
 * <p>CDP keeps {@code event.*} fields only when they are nested, as in {@code
 * "event":{"duration":203000000}}, and drops flat dotted keys such as {@code "event.duration"}
 * without an error. The parent encoder writes MDC entries as flat keys and ignores key-value pairs,
 * so a line that needs {@code event.*} fields sets them as key-value pairs and this encoder nests
 * them. A {@link Number} value is written as a JSON number, which CDP needs for {@code
 * event.duration}; a non-finite one, which JSON cannot hold, is written as a string. A line with no {@code event.*} key-value pairs is written exactly as the parent
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
    // The parent's serializers each end with a comma, and it removes only the last one.
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

  // JSON has no NaN or Infinity, so a bare one would make the whole line unparseable.
  private static boolean isFinite(Number number) {
    return switch (number) {
      case Double d -> Double.isFinite(d);
      case Float f -> Float.isFinite(f);
      default -> true;
    };
  }
}
