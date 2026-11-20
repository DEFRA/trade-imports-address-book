package uk.gov.defra.trade.imports.addressbook.tracing;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.ReplaceOptions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.bson.Document;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import uk.gov.defra.trade.imports.addressbook.filter.RequestTracingFilter;

/**
 * Wraps a collection so that, while the MDC holds a {@code trace.id}, each {@code find}, each
 * {@code insertOne} of a document alone, and each call whose last argument is {@link
 * CountOptions}, {@link InsertOneOptions} or {@link ReplaceOptions} sends that id as the command's
 * {@code comment}. A collection made from it by a {@code with...} method is wrapped the same way.
 * Every other call, and every call made while the MDC holds no {@code trace.id}, runs unchanged.
 */
public final class TraceIdCommentedCollection {

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
    Object[] arguments = args == null ? new Object[0] : args;
    String traceId = MDC.get(RequestTracingFilter.MDC_TRACE_ID);
    Object result =
        traceId == null
            ? call(target, method, arguments)
            : callWithComment(target, method, arguments, traceId);
    return result instanceof MongoCollection<?> derived ? wrap(derived) : result;
  }

  private static Object callWithComment(
      MongoCollection<?> target, Method method, Object[] args, String traceId) {
    if (isInsertOneOfADocumentAlone(method)) {
      return insertOne(target, args[0], traceId);
    }
    if (args.length > 0) {
      commentOptions(args[args.length - 1], traceId);
    }
    Object result = call(target, method, args);
    return result instanceof FindIterable<?> find ? find.comment(traceId) : result;
  }

  private static boolean isInsertOneOfADocumentAlone(Method method) {
    return method.getName().equals("insertOne") && method.getParameterCount() == 1;
  }

  @SuppressWarnings("unchecked")
  private static Object insertOne(MongoCollection<?> target, Object document, String traceId) {
    return ((MongoCollection<Object>) target)
        .insertOne(document, new InsertOneOptions().comment(traceId));
  }

  private static void commentOptions(Object lastArgument, String traceId) {
    if (lastArgument instanceof CountOptions count) {
      count.comment(traceId);
    } else if (lastArgument instanceof InsertOneOptions insert) {
      insert.comment(traceId);
    } else if (lastArgument instanceof ReplaceOptions replace) {
      replace.comment(traceId);
    }
  }

  private static Object call(MongoCollection<?> target, Method method, Object[] args) {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw driverFailureOf(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot call MongoCollection." + method.getName(), e);
    }
  }

  private static RuntimeException driverFailureOf(InvocationTargetException failure) {
    if (failure.getCause() instanceof Error error) {
      throw error;
    }
    return (RuntimeException) failure.getCause();
  }
}
