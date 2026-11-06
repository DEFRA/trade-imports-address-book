package uk.gov.defra.trade.imports.addressbook.transporter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.gov.defra.trade.imports.addressbook.ListPaging;
import uk.gov.defra.trade.imports.addressbook.SearchTerms;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

/** Lists, creates, reads, updates and soft-deletes an organisation's transporters. */
@Service
@Slf4j
public class TransporterService {

  /**
   * What a caller is told when a transporter is not theirs, as well as when it does not exist: a
   * cross-organisation request and an unknown id are deliberately indistinguishable, so the address
   * book never confirms that another organisation holds a given id.
   */
  static final String TRANSPORTER_NOT_FOUND = "Transporter not found";

  private final TransporterRepository repository;
  private final TransporterMapper transporterMapper;
  private final MeterRegistry meterRegistry;
  private final AddressTtlConfig ttlConfig;

  public TransporterService(
      TransporterRepository repository,
      TransporterMapper transporterMapper,
      MeterRegistry meterRegistry,
      AddressTtlConfig ttlConfig) {
    this.repository = repository;
    this.transporterMapper = transporterMapper;
    this.meterRegistry = meterRegistry;
    this.ttlConfig = ttlConfig;
  }

  /**
   * One page of the organisation's live transporters, newest first.
   *
   * @param organisationId the owning organisation id, from the identity header
   * @param page the 1-based page number
   * @param requestedPageSize the page size from {@link ListPaging#MIN_PAGE_SIZE} to {@link
   *     ListPaging#MAX_PAGE_SIZE}, or null for {@link ListPaging#DEFAULT_PAGE_SIZE}
   * @param filters the request's filters, each optional
   * @return one page of live transporters with pagination metadata
   * @throws uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException if {@code page}
   *     is less than 1 or {@code requestedPageSize} is outside {@link ListPaging#MIN_PAGE_SIZE} to
   *     {@link ListPaging#MAX_PAGE_SIZE}
   */
  public TransporterPageResponse list(
      String organisationId,
      int page,
      Integer requestedPageSize,
      RequestedTransporterListFilters filters) {
    ListPaging.requirePositivePage(page);
    int pageSize = ListPaging.servedPageSize(requestedPageSize);

    Pageable pageable =
        PageRequest.of(
            page - 1,
            pageSize,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Page<Transporter> result =
          repository.listLiveTransporters(organisationId, criteriaOf(filters), pageable);
      List<TransporterResponse> items =
          result.getContent().stream().map(transporterMapper::toResponse).toList();
      return new TransporterPageResponse(
          items, page, pageSize, (int) result.getTotalElements(), result.getTotalPages());
    } finally {
      sample.stop(meterRegistry.timer("controller.listTransporters.query.time"));
    }
  }

  private static TransporterListCriteria criteriaOf(RequestedTransporterListFilters requested) {
    return new TransporterListCriteria(requested.types(), matchesOf(requested));
  }

  private static List<TransporterListCriteria.Match> matchesOf(
      RequestedTransporterListFilters requested) {
    List<TransporterListCriteria.Match> matches = new ArrayList<>();
    if (!requested.searchTerm().isEmpty()) {
      matches.add(
          new TransporterListCriteria.TextMatching(
              SearchTerms.toPartialMatchRegex(requested.searchTerm())));
    }
    if (!requested.countryCodes().isEmpty()) {
      matches.add(new TransporterListCriteria.InCountries(requested.countryCodes()));
    }
    if (!requested.searchTypes().isEmpty()) {
      matches.add(new TransporterListCriteria.OfType(requested.searchTypes()));
    }
    return matches;
  }

  /**
   * Saves a new {@code ACTIVE} transporter owned by {@code organisationId}. Outside prod, when a
   * TTL is configured, it is given an {@code expireAt} counted from now, which a later update or
   * soft delete leaves as is.
   *
   * @param request the validated create body (client-supplied fields only)
   * @param organisationId the owning organisation id
   * @return the saved transporter, with its assigned id and timestamps
   */
  public Transporter create(TransporterRequest request, String organisationId) {
    Transporter transporter = transporterMapper.toEntity(request);
    transporter.setOrganisationId(organisationId);
    transporter.setStatus(TransporterStatus.ACTIVE);
    stampExpiry(transporter);

    Transporter saved = repository.save(transporter);
    log.info("Created transporter {}", saved.getId());
    return saved;
  }

  private void stampExpiry(Transporter transporter) {
    Integer days = ttlConfig.days();
    if (days == null || ttlConfig.isProd()) {
      return;
    }
    transporter.setExpireAt(Instant.now().plus(days, ChronoUnit.DAYS));
  }

  /**
   * Fetches one transporter by id within the caller's organisation, soft-deleted transporters
   * included.
   *
   * @param id the transporter id
   * @param organisationId the caller's organisation id
   * @return the transporter, with {@code status DELETED} if it was soft-deleted; empty if the id
   *     is unknown or belongs to another organisation
   */
  public Optional<Transporter> get(String id, String organisationId) {
    return repository.findByIdAndOrganisationId(id, organisationId);
  }

  /**
   * Replaces every client-supplied field of a live transporter in the caller's organisation. Its
   * {@code id}, {@code organisationId}, {@code status} and {@code createdAt} are kept, and {@code
   * modifiedAt} is bumped.
   *
   * @param id the transporter id
   * @param request the validated update body (client-supplied fields only)
   * @param organisationId the caller's organisation id
   * @return the updated transporter
   * @throws NotFoundException if the id is unknown, belongs to another organisation, or is
   *     soft-deleted
   */
  public Transporter update(String id, TransporterRequest request, String organisationId) {
    Transporter existing =
        repository
            .findByIdAndOrganisationId(id, organisationId)
            .filter(transporter -> transporter.getStatus() != TransporterStatus.DELETED)
            .orElseThrow(() -> NotFoundException.transporterNotFound(id, organisationId));

    transporterMapper.updateEntity(request, existing);

    Transporter saved = repository.save(existing);
    log.info("Updated transporter {}", saved.getId());
    return saved;
  }

  /**
   * Sets a transporter in the caller's organisation to {@code status DELETED} and bumps its
   * {@code modifiedAt}; the transporter is kept. A transporter that is already {@code DELETED} is
   * left unchanged.
   *
   * @param id the transporter id
   * @param organisationId the caller's organisation id
   * @throws NotFoundException if the id is unknown or belongs to another organisation
   */
  public void delete(String id, String organisationId) {
    Transporter existing =
        repository
            .findByIdAndOrganisationId(id, organisationId)
            .orElseThrow(() -> NotFoundException.transporterNotFound(id, organisationId));

    if (existing.getStatus() == TransporterStatus.DELETED) {
      return;
    }

    existing.setStatus(TransporterStatus.DELETED);
    repository.save(existing);
    log.info("Soft-deleted transporter {}", existing.getId());
  }
}
