package uk.gov.defra.trade.imports.addressbook.tracing;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.ReplaceOptions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bson.Document;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import uk.gov.defra.trade.imports.addressbook.filter.RequestTracingFilter;

/**
 * Wraps a collection so that, while the MDC holds a {@code trace.id}, each {@code find}, {@code
 * countDocuments}, {@code insertOne} and {@code replaceOne} it runs sends that id as the command's
 * {@code comment}. A collection made from it by a {@code with...} method is wrapped the same way.
 * Every other method, and every call made while the MDC holds no {@code trace.id}, runs unchanged.
 */
public final class TraceIdCommentedCollection {

  private static final Map<String, Supplier<Object>> NEW_OPTIONS_BY_METHOD =
      Map.of(
          "countDocuments", CountOptions::new,
          "insertOne", InsertOneOptions::new,
          "replaceOne", ReplaceOptions::new);

  private TraceIdCommentedCollection() {}

  /**
   * Wraps a collection.
   *
   * @param collection the collection whose commands carry the trace id
   * @return the wrapped collection
   */
  @SuppressWarnings("unchecked")
  public static MongoCollection<Document> of(MongoCollection<Document> collection) {
    return (MongoCollection<Document>) wrap(collection);
  }

  private static Object wrap(MongoCollection<?> collection) {
    return Proxy.newProxyInstance(
        MongoCollection.class.getClassLoader(),
        new Class<?>[] {MongoCollection.class},
        (proxy, method, args) -> invoke(collection, method, args));
  }

  private static Object invoke(MongoCollection<?> target, Method method, @Nullable Object[] args) {
    String traceId = MDC.get(RequestTracingFilter.MDC_TRACE_ID);
    Supplier<Object> newOptions = NEW_OPTIONS_BY_METHOD.get(method.getName());
    Object result =
        traceId == null || newOptions == null
            ? call(target, method, args)
            : callWithComment(target, method, args, newOptions.get(), traceId);
    if (result instanceof MongoCollection<?> derived) {
      return wrap(derived);
    }
    if (traceId != null && result instanceof FindIterable<?> find) {
      return find.comment(traceId);
    }
    return result;
  }

  private static Object callWithComment(
      MongoCollection<?> target,
      Method method,
      @Nullable Object[] args,
      Object newOptions,
      String traceId) {
    Object[] arguments = args == null ? new Object[0] : args;
    Class<?> optionsType = newOptions.getClass();
    if (arguments.length > 0 && optionsType.isInstance(arguments[arguments.length - 1])) {
      comment(arguments[arguments.length - 1], traceId);
      return call(target, method, arguments);
    }
    Optional<Method> withOptions = overloadAlsoTaking(method, optionsType);
    if (withOptions.isEmpty()) {
      return call(target, method, arguments);
    }
    comment(newOptions, traceId);
    return call(target, withOptions.get(), appended(arguments, newOptions));
  }

  private static void comment(Object options, String traceId) {
    switch (options) {
      case CountOptions count -> count.comment(traceId);
      case InsertOneOptions insert -> insert.comment(traceId);
      case ReplaceOptions replace -> replace.comment(traceId);
      default ->
          throw new IllegalArgumentException(
              "No comment for options " + options.getClass().getName());
    }
  }

  private static Optional<Method> overloadAlsoTaking(Method method, Class<?> optionsType) {
    try {
      return Optional.of(
          MongoCollection.class.getMethod(
              method.getName(), appended(method.getParameterTypes(), optionsType)));
    } catch (NoSuchMethodException _) {
      return Optional.empty();
    }
  }

  private static <T> T[] appended(T[] values, T last) {
    T[] longer = Arrays.copyOf(values, values.length + 1);
    longer[values.length] = last;
    return longer;
  }

  private static Object call(MongoCollection<?> target, Method method, @Nullable Object[] args) {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw uncheckedCauseOf(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot call MongoCollection." + method.getName(), e);
    }
  }

  private static RuntimeException uncheckedCauseOf(InvocationTargetException failure) {
    return switch (failure.getCause()) {
      case RuntimeException runtime -> runtime;
      case Error error -> throw error;
      case Throwable checked -> new UndeclaredThrowableException(checked);
    };
  }
}
