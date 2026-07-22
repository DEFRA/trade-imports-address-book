package uk.gov.defra.trade.imports.operators.operator;

/**
 * Static entity &harr; DTO mapping. No framework, no reflection — the wire contract is owned here,
 * decoupled from the {@code @Document} entity which is never serialised.
 */
public final class OperatorMapper {

  private OperatorMapper() {}

  /** Maps a persisted {@link Address} onto its wire response, deriving {@code deleted} from status. */
  public static OperatorResponse toResponse(Address address) {
    return OperatorResponse.builder()
        .id(address.getId())
        .name(address.getName())
        .addressLine1(address.getAddressLine1())
        .addressLine2(address.getAddressLine2())
        .townOrCity(address.getTownOrCity())
        .county(address.getCounty())
        .postcode(address.getPostcode())
        .countryCode(address.getCountryCode())
        .phone(address.getPhone())
        .email(address.getEmail())
        .organisationId(address.getOrganisationId())
        .deleted(address.getStatus() == AddressStatus.DELETED)
        .createdAt(address.getCreatedAt())
        .modifiedAt(address.getModifiedAt())
        .build();
  }

  /**
   * Maps a client request onto a new entity carrying only the client-supplied fields. Server-owned
   * fields ({@code id}, {@code organisationId}, {@code status}, timestamps) are set by the service
   * on create/update, not here.
   */
  public static Address toEntity(AddressRequest request) {
    return Address.builder()
        .name(request.name())
        .addressLine1(request.addressLine1())
        .addressLine2(request.addressLine2())
        .townOrCity(request.townOrCity())
        .county(request.county())
        .postcode(request.postcode())
        .countryCode(request.countryCode())
        .phone(request.phone())
        .email(request.email())
        .build();
  }
}
