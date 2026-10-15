package uk.gov.defra.trade.imports.addressbook.address;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
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
  @BeanMapping(ignoreUnmappedSourceProperties = {"status", "version", "expireAt"})
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
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "expireAt", ignore = true)
  @Mapping(target = "uses", source = "uses", qualifiedByName = "toUses")
  Address toEntity(AddressRequest request);

  /**
   * Applies a full replace of client-supplied fields onto an existing entity. Server-owned fields
   * are preserved by the caller after this mapping runs. Optional fields are replaced too, including
   * {@code addressType} and {@code uses}, so an update without them clears them.
   */
  @BeanMapping(ignoreUnmappedSourceProperties = {"type", "role"})
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organisationId", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "expireAt", ignore = true)
  @Mapping(target = "uses", source = "uses", qualifiedByName = "toUses")
  void updateEntity(AddressRequest request, @MappingTarget Address address);

  /** Replaces, never mutates, the stored list; names are already checked by {@link EnumNames}. */
  @Named("toUses")
  default List<AddressUse> toUses(List<String> uses) {
    return uses == null
        ? null
        : uses.stream().map(AddressUse::valueOf).collect(Collectors.toCollection(ArrayList::new));
  }
}
