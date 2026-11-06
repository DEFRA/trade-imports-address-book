package uk.gov.defra.trade.imports.addressbook.exceptions;

/**
 * An address the caller may not see: an unknown id, another organisation's id, or a path {@code
 * orgId} that disagrees with the caller's organisation. Every case shows the caller the same
 * {@link #detail()}; the message names the case and its ids.
 */
public class NotFoundException extends RuntimeException {

  private NotFoundException(String message) {
    super(message);
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
        "Address " + addressId + " not found in organisation " + organisationId);
  }

  /**
   * A path {@code orgId} that disagrees with the caller's organisation id.
   *
   * @param orgId the organisation id from the path
   * @param organisationId the caller's organisation id, from the identity header
   * @return the exception, its message naming both organisation ids
   */
  public static NotFoundException organisationMismatch(String orgId, String organisationId) {
    return new NotFoundException(
        "Organisation mismatch: path orgId "
            + orgId
            + " is not the caller's organisation "
            + organisationId);
  }

  /**
   * The text shown to the caller, the same for every case.
   *
   * @return the caller-facing detail
   */
  public String detail() {
    return "Address not found";
  }
}
