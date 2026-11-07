package uk.gov.defra.trade.imports.addressbook.transporter;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.defra.trade.imports.addressbook.address.ClassLevelViolations;

public class CommercialTransporterCountryValidator
    implements ConstraintValidator<CommercialTransporterCountry, TransporterRequest> {

  /** The fixed country code of a commercial transporter: Northern Ireland. */
  public static final String NORTHERN_IRELAND_COUNTRY_CODE = "GB-NIR";

  @Override
  public boolean isValid(TransporterRequest request, ConstraintValidatorContext context) {
    if (request.type() != TransporterType.COMMERCIAL) {
      return true;
    }
    if (NORTHERN_IRELAND_COUNTRY_CODE.equals(request.countryCode())) {
      return true;
    }
    return ClassLevelViolations.reportOn(context, "countryCode");
  }
}
