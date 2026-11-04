package uk.gov.defra.trade.imports.addressbook.address;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;

/** Lists, creates, reads, updates and soft-deletes an organisation's addresses. */
@Service
@Slf4j
public class AddressService {

  static final int MIN_PAGE_SIZE = 1;
  static final int MAX_PAGE_SIZE = 100;
  public static final int DEFAULT_PAGE_SIZE = 25;

  private final AddressRepository repository;
  private final AddressMapper addressMapper;
  private final MeterRegistry meterRegistry;
  private final AddressTtlConfig ttlConfig;

  public AddressService(
      AddressRepository repository,
      AddressMapper addressMapper,
      MeterRegistry meterRegistry,
      AddressTtlConfig ttlConfig) {
    this.repository = repository;
    this.addressMapper = addressMapper;
    this.meterRegistry = meterRegistry;
    this.ttlConfig = ttlConfig;
  }

  /**
   * One page of the organisation's live addresses, newest first.
   *
   * @param organisationId the owning organisation id, from the identity header
   * @param page the 1-based page number
   * @param requestedPageSize the page size from {@link #MIN_PAGE_SIZE} to {@link #MAX_PAGE_SIZE},
   *     or null for {@link #DEFAULT_PAGE_SIZE}
   * @param filters the request's filters, each optional. The search term is a case-insensitive
   *     partial-word search over name, address line 1, address line 2, town or city, county and
   *     postcode; an address in any of the country codes, or holding any of the search uses,
   *     matches too, so an address matching the term, a code or a search use is returned. The
   *     address type lists the addresses holding its uses ({@code ORIGIN_OR_CONSIGNOR} also lists
   *     an address with no uses), and the use keeps only addresses holding it; both combine with
   *     the search
   * @return one page of live addresses with pagination metadata
   * @throws BadRequestException if {@code page} is less than 1 or {@code requestedPageSize} is
   *     outside {@link #MIN_PAGE_SIZE} to {@link #MAX_PAGE_SIZE}
   */
  public AddressPageResponse list(
      String organisationId, int page, Integer requestedPageSize, RequestedListFilters filters) {
    if (page < 1) {
      throw new BadRequestException("page must be 1 or greater");
    }
    int pageSize = servedPageSize(requestedPageSize);

    Pageable pageable =
        PageRequest.of(
            page - 1,
            pageSize,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Page<Address> result =
          repository.listLiveAddresses(organisationId, criteriaOf(filters), pageable);
      List<AddressResponse> items =
          result.getContent().stream().map(addressMapper::toResponse).toList();
      return new AddressPageResponse(
          items, page, pageSize, (int) result.getTotalElements(), result.getTotalPages());
    } finally {
      sample.stop(meterRegistry.timer("controller.listOperators.query.time"));
    }
  }

  private int servedPageSize(Integer requestedPageSize) {
    if (requestedPageSize == null) {
      return DEFAULT_PAGE_SIZE;
    }
    if (requestedPageSize < MIN_PAGE_SIZE || requestedPageSize > MAX_PAGE_SIZE) {
      throw new BadRequestException(
          "pageSize must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
    }
    return requestedPageSize;
  }

  private static ListCriteria criteriaOf(RequestedListFilters filters) {
    return new ListCriteria(
        filters.addressType(),
        filters.use(),
        searchPatternOf(filters.searchTerm()),
        nonBlankTrimmed(filters.countryCodes()),
        presentElements(filters.searchUses()).toList());
  }

  private static String searchPatternOf(String searchTerm) {
    return searchTerm == null || searchTerm.isBlank() ? null : toPartialMatchRegex(searchTerm);
  }

  private static List<String> nonBlankTrimmed(List<String> countryCodes) {
    return presentElements(countryCodes)
        .filter(code -> !code.isBlank())
        .map(String::trim)
        .toList();
  }

  private static <T> Stream<T> presentElements(List<T> values) {
    return values == null ? Stream.empty() : values.stream().filter(Objects::nonNull);
  }

  private static String toPartialMatchRegex(String searchTerm) {
    return ".*" + Pattern.quote(searchTerm.trim()) + ".*";
  }

  /**
   * Saves a new {@code ACTIVE} address owned by {@code organisationId}. Outside prod, when a TTL is
   * configured, it is given an {@code expireAt} counted from now, which a later update or soft
   * delete leaves as is.
   *
   * @param request the validated create body (client-supplied fields only)
   * @param organisationId the owning organisation id
   * @return the saved address, with its assigned id and timestamps
   */
  public Address create(AddressRequest request, String organisationId) {
    Address address = addressMapper.toEntity(request);
    address.setOrganisationId(organisationId);
    address.setStatus(AddressStatus.ACTIVE);
    stampExpiry(address);

    Address saved = repository.save(address);
    log.info("Created address {}", saved.getId());
    return saved;
  }

  private void stampExpiry(Address address) {
    Integer days = ttlConfig.days();
    if (days == null || ttlConfig.isProd()) {
      return;
    }
    address.setExpireAt(Instant.now().plus(days, ChronoUnit.DAYS));
  }

  /**
   * Fetches one address by id within the caller's organisation, soft-deleted addresses included.
   *
   * @param id the address id
   * @param organisationId the caller's organisation id
   * @return the address, with {@code status DELETED} if it was soft-deleted; empty if the id is
   *     unknown or belongs to another organisation
   */
  public Optional<Address> get(String id, String organisationId) {
    return repository.findByIdAndOrganisationId(id, organisationId);
  }

  /**
   * Replaces every client-supplied field of a live address in the caller's organisation. Its {@code
   * id}, {@code organisationId}, {@code status} and {@code createdAt} are kept, and {@code
   * modifiedAt} is bumped.
   *
   * @param id the address id
   * @param request the validated update body (client-supplied fields only)
   * @param organisationId the caller's organisation id
   * @return the updated address
   * @throws NotFoundException if the id is unknown, belongs to another organisation, or is
   *     soft-deleted
   */
  public Address update(String id, AddressRequest request, String organisationId) {
    Address existing =
        repository
            .findByIdAndOrganisationId(id, organisationId)
            .filter(address -> address.getStatus() != AddressStatus.DELETED)
            .orElseThrow(() -> new NotFoundException("Address not found"));

    addressMapper.updateEntity(request, existing);

    Address saved = repository.save(existing);
    log.info("Updated address {}", saved.getId());
    return saved;
  }

  /**
   * Sets an address in the caller's organisation to {@code status DELETED} and bumps its {@code
   * modifiedAt}; the address is kept. An address that is already {@code DELETED} is left unchanged.
   *
   * @param id the address id
   * @param organisationId the caller's organisation id
   * @throws NotFoundException if the id is unknown or belongs to another organisation
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
