package uk.gov.defra.trade.imports.addressbook.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

public final class CapturedLogs {

  private CapturedLogs() {}

  public static List<ILoggingEvent> loggedBy(Class<?> loggerOwner, Runnable action) {
    Logger logger = (Logger) LoggerFactory.getLogger(loggerOwner);
    ListAppender<ILoggingEvent> appender = new MdcKeepingListAppender();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
    }
    return appender.list;
  }

  public static Map<String, Object> keyValues(ILoggingEvent event) {
    Map<String, Object> keyValues = new HashMap<>();
    if (event.getKeyValuePairs() != null) {
      event.getKeyValuePairs().forEach(pair -> keyValues.put(pair.key, pair.value));
    }
    return keyValues;
  }

  private static final class MdcKeepingListAppender extends ListAppender<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      super.append(event);
    }
  }
}
