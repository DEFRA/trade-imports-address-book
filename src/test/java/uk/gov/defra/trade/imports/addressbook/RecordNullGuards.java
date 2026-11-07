package uk.gov.defra.trade.imports.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

/**
 * Proves a record's compact constructor rejects a null reference-typed component by naming it in
 * the {@link NullPointerException} it throws, for every component except the ones listed
 * optional.
 */
public final class RecordNullGuards {

  private RecordNullGuards() {}

  /**
   * @param type the record type under test
   * @param validValues a valid value for every component, keyed by component name — including the
   *     optional ones and any primitive, which {@code newInstance} still needs a boxed value for
   * @param optionalComponents the component names allowed to be null
   */
  public static <T extends Record> void assertEveryRequiredComponentRejectsNull(
      Class<T> type, Map<String, Object> validValues, Set<String> optionalComponents) {
    RecordComponent[] components = type.getRecordComponents();
    Class<?>[] parameterTypes = new Class<?>[components.length];
    Object[] validArgs = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      parameterTypes[i] = components[i].getType();
      validArgs[i] = validValues.get(components[i].getName());
    }
    Constructor<T> constructor = canonicalConstructorOf(type, parameterTypes);
    for (int i = 0; i < components.length; i++) {
      String componentName = components[i].getName();
      if (parameterTypes[i].isPrimitive() || optionalComponents.contains(componentName)) {
        continue;
      }
      Object[] args = validArgs.clone();
      args[i] = null;
      assertThat(nullPointerExceptionMessageFrom(constructor, args))
          .as("building %s with %s null", type.getSimpleName(), componentName)
          .isEqualTo(componentName);
    }
  }

  private static <T> Constructor<T> canonicalConstructorOf(Class<T> type, Class<?>[] parameterTypes) {
    try {
      return type.getDeclaredConstructor(parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("No canonical constructor for " + type, e);
    }
  }

  private static <T> String nullPointerExceptionMessageFrom(Constructor<T> constructor, Object[] args) {
    try {
      constructor.newInstance(args);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof NullPointerException nullPointerException) {
        return nullPointerException.getMessage();
      }
      throw new AssertionError("Expected a NullPointerException", e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    throw new AssertionError("Expected a NullPointerException but none was thrown");
  }
}
