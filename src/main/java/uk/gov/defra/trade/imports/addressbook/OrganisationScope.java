package uk.gov.defra.trade.imports.addressbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

/** Confirms a path-scoped {@code orgId} matches the caller's trusted organisation header. */
@Slf4j
public final class OrganisationScope {

  private OrganisationScope() {}

  /**
   * @param resource the kind of resource being scoped, for the refusal log line
   * @param resourceId the resource's own id, or null when the request scopes the organisation
   *     itself rather than one record (a list or a create)
   * @param pathOrganisationId the organisation scope from the request path
   * @param callerOrganisationId the caller's organisation id, from the identity header
   * @param notFoundMessage the message to raise when the two disagree
   * @throws NotFoundException if {@code pathOrganisationId} does not match {@code
   *     callerOrganisationId}
   */
  public static void authorise(
      String resource,
      @Nullable String resourceId,
      String pathOrganisationId,
      String callerOrganisationId,
      String notFoundMessage) {
    if (!callerOrganisationId.equals(pathOrganisationId)) {
      log.warn(
          "Refusing {} {} scoped to organisation {} for caller organisation {}",
          resource,
          resourceId,
          pathOrganisationId,
          callerOrganisationId);
      throw new NotFoundException(notFoundMessage);
    }
  }
}
