package uk.gov.defra.trade.imports.addressbook.transporter;

import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.SAMPLE_ADDRESS_LINE_1;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.SAMPLE_EMAIL;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.SAMPLE_NAME;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.SAMPLE_PHONE;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.SAMPLE_POSTCODE;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.SAMPLE_TOWN_OR_CITY;

import java.time.Instant;

public final class TransporterTestSupport {

  public static final String NORTHERN_IRELAND_COUNTRY_CODE =
      CommercialTransporterCountryValidator.NORTHERN_IRELAND_COUNTRY_CODE;
  public static final String SAMPLE_APPROVAL_NUMBER = "UK/BELF/T2/00104115";

  static final String STORED_TRANSPORTER_ID = "775f1c2ab3e4d51a2c9d0e88";
  static final Instant STORED_CREATED_AT = Instant.parse("2026-07-01T09:00:00Z");
  static final Instant STORED_MODIFIED_AT = Instant.parse("2026-07-02T10:30:00Z");

  private TransporterTestSupport() {}

  public static TransporterRequest.TransporterRequestBuilder validPrivateRequest() {
    return TransporterRequest.builder()
        .name(SAMPLE_NAME)
        .addressLine1(SAMPLE_ADDRESS_LINE_1)
        .townOrCity(SAMPLE_TOWN_OR_CITY)
        .postcode(SAMPLE_POSTCODE)
        .countryCode("FR")
        .phone(SAMPLE_PHONE)
        .email(SAMPLE_EMAIL)
        .type(TransporterType.PRIVATE);
  }

  public static TransporterRequest.TransporterRequestBuilder validCommercialRequest() {
    return validPrivateRequest()
        .countryCode(NORTHERN_IRELAND_COUNTRY_CODE)
        .type(TransporterType.COMMERCIAL)
        .approvalNumber(SAMPLE_APPROVAL_NUMBER);
  }

  static Transporter.TransporterBuilder storedTransporter(
      String organisationId, TransporterStatus status) {
    return Transporter.builder()
        .id(STORED_TRANSPORTER_ID)
        .name(SAMPLE_NAME)
        .addressLine1(SAMPLE_ADDRESS_LINE_1)
        .townOrCity(SAMPLE_TOWN_OR_CITY)
        .postcode(SAMPLE_POSTCODE)
        .countryCode(NORTHERN_IRELAND_COUNTRY_CODE)
        .phone(SAMPLE_PHONE)
        .email(SAMPLE_EMAIL)
        .type(TransporterType.COMMERCIAL)
        .approvalNumber(SAMPLE_APPROVAL_NUMBER)
        .organisationId(organisationId)
        .status(status)
        .createdAt(STORED_CREATED_AT)
        .modifiedAt(STORED_MODIFIED_AT);
  }
}
