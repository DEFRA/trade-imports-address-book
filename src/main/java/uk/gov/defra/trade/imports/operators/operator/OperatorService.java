package uk.gov.defra.trade.imports.operators.operator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.gov.defra.trade.imports.operators.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.operators.exceptions.NotFoundException;

/**
 * Application service for addresses. Owns the create/read/update/delete business rules; the wire
 * contract lives on the response records and the persistence shape on the {@link Address} entity.
 *
 * <p>On create the owning {@code organisationId} is stamped from the trusted forwarded header
 * (cv-010), never taken from the body, and the address starts {@code ACTIVE}. {@code id} is left for
 * Mongo and the timestamps for auditing.
 */
@Service
@Slf4j
public class OperatorService {

  private final OperatorRepository repository;
  private final MeterRegistry meterRegistry;
  private final int pageSize;

  public OperatorService(
      OperatorRepository repository,
      MeterRegistry meterRegistry,
      @Value("${address-book.list.page-size:25}") int pageSize) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
    this.pageSize = pageSize;
  }

  /**
   * One page of the organisation's ACTIVE addresses, newest first. Scoped to {@code organisationId};
   * DELETED tombstones are excluded because the query pins {@code status ACTIVE}. {@code page} is
   * 1-based and translated to Spring Data's 0-based index; the page size is a server-side config
   * (cv-025), not a request parameter; the sort is {@code createdAt} descending, served by the
   * {@code org_status_created} index.
   *
   * <p>An out-of-range {@code page} (&lt; 1) is a {@link BadRequestException}.
   *
   * @param organisationId the owning organisation id, from the identity header
   * @param page the 1-based page number
   * @return one page of ACTIVE addresses with pagination metadata
   * @throws BadRequestException if {@code page} is less than 1
   */
  public OperatorPageResponse list(String organisationId, int page) {
    if (page < 1) {
      throw new BadRequestException("page must be 1 or greater");
    }

    Pageable pageable =
        PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

    Timer.Sample sample = Timer.start(meterRegistry);
    Page<Address> result =
        repository.findByOrganisationIdAndStatus(organisationId, AddressStatus.ACTIVE, pageable);
    sample.stop(meterRegistry.timer("OperatorListQuery"));

    List<OperatorResponse> items =
        result.getContent().stream().map(OperatorMapper::toResponse).toList();
    return new OperatorPageResponse(
        items, page, pageSize, (int) result.getTotalElements(), result.getTotalPages());
  }

  /**
   * Persists a new address owned by the calling organisation.
   *
   * @param request the validated create body (client-supplied fields only)
   * @param organisationId the owning organisation id, from the identity header
   * @return the persisted address, with its Mongo-assigned id and audited timestamps
   */
  public Address create(AddressRequest request, String organisationId) {
    Address address = OperatorMapper.toEntity(request);
    address.setOrganisationId(organisationId);
    address.setStatus(AddressStatus.ACTIVE);

    Address saved = repository.save(address);
    log.info("Created address {}", saved.getId());
    return saved;
  }

  /**
   * Fetches one address by id within the caller's organisation scope, tombstones included. A
   * soft-deleted address is returned WITH {@code status DELETED} so a consumer can tell "deleted"
   * (present, DELETED) from "unknown or not yours" (empty &rarr; 404). An id outside the caller's
   * organisation is indistinguishable from an unknown id: both are empty, so 404 leaks no existence
   * across organisations (cv-010).
   *
   * @param id the address id
   * @param organisationId the caller's organisation id, from the identity header
   * @return the address if it exists in the caller's scope, otherwise empty
   */
  public Optional<Address> get(String id, String organisationId) {
    return repository.findByIdAndOrganisationId(id, organisationId);
  }

  /**
   * Replaces an address's mutable fields within the caller's organisation scope (full replace). The
   * wire can never move the server-owned fields: {@code id}, {@code organisationId}, {@code status}
   * and {@code createdAt} are all preserved from the stored entity; {@code modifiedAt} is bumped by
   * auditing on save (audit only). An unknown id, an id owned by another organisation, or a
   * soft-deleted tombstone are all outside the caller's live set and yield a 404.
   *
   * @param id the address id
   * @param request the validated update body (client-supplied fields only)
   * @param organisationId the caller's organisation id, from the identity header
   * @return the updated address, with its bumped {@code modifiedAt}
   * @throws NotFoundException if the id is unknown, out of scope, or a tombstone
   */
  public Address update(String id, AddressRequest request, String organisationId) {
    Address existing =
        repository
            .findByIdAndOrganisationId(id, organisationId)
            .filter(address -> address.getStatus() != AddressStatus.DELETED)
            .orElseThrow(() -> new NotFoundException("Address not found"));

    existing.setName(request.name());
    existing.setAddressLine1(request.addressLine1());
    existing.setAddressLine2(request.addressLine2());
    existing.setTownOrCity(request.townOrCity());
    existing.setCounty(request.county());
    existing.setPostcode(request.postcode());
    existing.setCountryCode(request.countryCode());
    existing.setPhone(request.phone());
    existing.setEmail(request.email());

    Address saved = repository.save(existing);
    log.info("Updated address {}", saved.getId());
    return saved;
  }

  /**
   * Soft-deletes an address within the caller's organisation scope. The document is
   * <strong>not</strong> removed: its {@code status} flips to {@code DELETED} and {@code modifiedAt}
   * is bumped on save. The tombstone stays fetchable by id so a consumer can tell "deleted" (200 +
   * DELETED) from "unknown or not yours" (404); a hard delete would collapse both into a 404 and
   * make deletion undetectable.
   *
   * <p>Idempotent: deleting an address that is already a tombstone is a no-op — no re-save, so
   * {@code modifiedAt} is not bumped a second time and the state is unchanged. An unknown id, or an
   * id owned by another organisation, is empty in the caller's scope and yields a 404.
   *
   * @param id the address id
   * @param organisationId the caller's organisation id, from the identity header
   * @throws NotFoundException if the id is unknown or out of the caller's organisation scope
   */
  public void delete(String id, String organisationId) {
    Address existing =
        repository
            .findByIdAndOrganisationId(id, organisationId)
            .orElseThrow(() -> new NotFoundException("Address not found"));

    if (existing.getStatus() == AddressStatus.DELETED) {
      return;
    }

    existing.setStatus(AddressStatus.DELETED);
    repository.save(existing);
    log.info("Soft-deleted address {}", existing.getId());
  }
}
