package uk.gov.defra.trade.imports.operators.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.defra.trade.imports.operators.operator.OperatorRequest;
import uk.gov.defra.trade.imports.operators.operator.OperatorType;

/**
 * Validator for {@link ValidTransporterFields}. Reports a per-field violation (property node
 * {@code approvalNumber} / {@code transporterCategory}) so the failure keys the same camelCase
 * {@code errors} map the field-level constraints do — the wire name is the Java field name.
 *
 * <p>A null or missing {@code operatorType} is left to {@code @NotNull} — an absent type is not a
 * non-TRANSPORTER type, so the cross-field rule stays silent and does not double up on the missing
 * mandatory field.
 */
public class TransporterFieldsValidator
    implements ConstraintValidator<ValidTransporterFields, OperatorRequest> {

  private static final String MESSAGE = "Only allowed for transporter operators";

  @Override
  public boolean isValid(OperatorRequest request, ConstraintValidatorContext context) {
    if (request == null
        || request.operatorType() == null
        || request.operatorType() == OperatorType.TRANSPORTER) {
      return true;
    }

    boolean valid = true;
    if (hasText(request.approvalNumber())) {
      addFieldViolation(context, "approvalNumber");
      valid = false;
    }
    if (request.transporterCategory() != null) {
      addFieldViolation(context, "transporterCategory");
      valid = false;
    }
    return valid;
  }

  private void addFieldViolation(ConstraintValidatorContext context, String property) {
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate(MESSAGE)
        .addPropertyNode(property)
        .addConstraintViolation();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
