package uk.gov.defra.trade.imports.addressbook.exceptions;

/**
 * A record the caller may not see: an unknown id, another organisation's id, or a path {@code
 * orgId} that disagrees with the caller's organisation. The caller sees only {@link #detail()};
 * the message names the case and its ids.
 */
public class NotFoundException extends RuntimeException {

  private final String detail;

  private NotFoundException(String detail, String message) {
    super(message);
    this.detail = detail;
  }

  /**
   * An address id with no address the caller's organisation may use.
   *
   * @param addressId the requested address id
   * @param organisationId the caller's organisation id
   * @return the exception, its message naming both ids
   */
  public static NotFoundException addressNotFound(String addressId, String organisationId) {
    return new NotFoundException(
        "Address not found",
        "Address " + addressId + " not found in organisation " + organisationId);
  }

  /**
   * A transporter id with no transporter the caller's organisation may use.
   *
   * @param transporterId the requested transporter id
   * @param organisationId the caller's organisation id
   * @return the exception, its message naming both ids
   */
  public static NotFoundException transporterNotFound(String transporterId, String organisationId) {
    return new NotFoundException(
        "Transporter not found",
        "Transporter " + transporterId + " not found in organisation " + organisationId);
  }

  /**
   * A path {@code orgId} that disagrees with the caller's organisation id.
   *
   * @param orgId the organisation id from the path
   * @param organisationId the caller's organisation id, from the identity header
   * @param detail the text shown to the caller
   * @return the exception, its message naming both organisation ids
   */
  public static NotFoundException organisationMismatch(
      String orgId, String organisationId, String detail) {
    return new NotFoundException(
        detail,
        "Organisation mismatch: path orgId "
            + orgId
            + " is not the caller's organisation "
            + organisationId);
  }

  /**
   * The text shown to the caller.
   *
   * @return the caller-facing detail
   */
  public String detail() {
    return detail;
  }
}
