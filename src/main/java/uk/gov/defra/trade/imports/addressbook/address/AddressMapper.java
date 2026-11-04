package uk.gov.defra.trade.imports.addressbook.address;

import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    unmappedSourcePolicy = ReportingPolicy.ERROR,
    collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
public interface AddressMapper {

  /**
   * Maps a persisted {@link Address} onto its wire response, deriving {@code deleted} from status.
   */
  @BeanMapping(ignoreUnmappedSourceProperties = {"status", "version", "expireAt"})
  @Mapping(target = "deleted", expression = "java(address.getStatus() == AddressStatus.DELETED)")
  AddressResponse toResponse(Address address);

  /** Maps the client-supplied fields of a request onto a new entity. */
  @BeanMapping(ignoreUnmappedSourceProperties = {"type", "role"})
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organisationId", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "expireAt", ignore = true)
  Address toEntity(AddressRequest request);

  /** Replaces every client-supplied field of an existing entity with the request's value. */
  @InheritConfiguration(name = "toEntity")
  void updateEntity(AddressRequest request, @MappingTarget Address address);
}
