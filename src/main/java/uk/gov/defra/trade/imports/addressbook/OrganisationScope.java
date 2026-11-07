package uk.gov.defra.trade.imports.addressbook;

import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

/** Confirms a path-scoped {@code orgId} matches the caller's trusted organisation header. */
public final class OrganisationScope {

  private OrganisationScope() {}

  /**
   * @param pathOrganisationId the organisation scope from the request path
   * @param callerOrganisationId the caller's organisation id, from the identity header
   * @param notFoundMessage the message to raise when the two disagree
   * @throws NotFoundException if {@code pathOrganisationId} does not match {@code
   *     callerOrganisationId}
   */
  public static void authorise(
      String pathOrganisationId, String callerOrganisationId, String notFoundMessage) {
    if (!callerOrganisationId.equals(pathOrganisationId)) {
      throw new NotFoundException(notFoundMessage);
    }
  }
}
