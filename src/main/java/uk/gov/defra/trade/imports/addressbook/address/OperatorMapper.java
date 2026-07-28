package uk.gov.defra.trade.imports.addressbook.address;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity ↔ DTO mapping. The wire contract is owned here, decoupled from the {@code @Document}
 * entity which is never serialised.
 *
 * <p>{@code unmappedTargetPolicy = ERROR} ensures a compile-time failure if a field is added to
 * {@link OperatorResponse} or {@link Address} without a corresponding mapping being wired up here.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface OperatorMapper {

  /**
   * Maps a persisted {@link Address} onto its wire response, deriving {@code deleted} from status.
   */
  @BeanMapping(ignoreUnmappedSourceProperties = "status")
  @Mapping(target = "deleted", expression = "java(address.getStatus() == AddressStatus.DELETED)")
  OperatorResponse toResponse(Address address);

  /**
   * Maps a client request onto a new entity carrying only the client-supplied fields. Server-owned
   * fields ({@code id}, {@code organisationId}, {@code status}, timestamps) are set by the service
   * on create/update, not here.
   */
  @BeanMapping(ignoreUnmappedSourceProperties = {"type", "role"})
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organisationId", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  Address toEntity(AddressRequest request);
}
