package uk.gov.defra.trade.imports.addressbook.transporter;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.defra.trade.imports.addressbook.address.ClassLevelViolations;

public class TransporterApprovalRequirementValidator
    implements ConstraintValidator<TransporterApprovalRequirement, TransporterRequest> {

  @Override
  public boolean isValid(TransporterRequest request, ConstraintValidatorContext context) {
    boolean hasApprovalNumber =
        request.approvalNumber() != null && !request.approvalNumber().isBlank();
    boolean isCommercial = request.type() == TransporterType.COMMERCIAL;

    if (isCommercial && !hasApprovalNumber) {
      return ClassLevelViolations.reportOn(context, "approvalNumber");
    }
    if (!isCommercial && hasApprovalNumber) {
      return ClassLevelViolations.reportOn(
          context, "approvalNumber", "A private transporter must not have an approval number");
    }
    return true;
  }
}
