package uk.gov.defra.trade.imports.addressbook;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * The Bean Validation {@link Validator} for this test package, plus the over-length text
 * generator used to build an over-maximum field. One factory for the whole test run: a validator
 * carries no per-test state, so there is nothing to tear down between tests.
 */
public final class ValidatorSupport {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private ValidatorSupport() {}

  public static Validator validator() {
    return VALIDATOR;
  }

  public static String repeatedText(int length) {
    return "a".repeat(length);
  }
}
