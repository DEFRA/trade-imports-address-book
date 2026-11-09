package uk.gov.defra.trade.imports.addressbook.transporter;

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
public interface TransporterMapper {

  @BeanMapping(ignoreUnmappedSourceProperties = {"status", "version", "expireAt"})
  @Mapping(
      target = "deleted",
      expression = "java(transporter.getStatus() == TransporterStatus.DELETED)")
  TransporterResponse toResponse(Transporter transporter);

  @BeanMapping(ignoreUnmappedSourceProperties = {"roles", "role"})
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organisationId", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "modifiedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "expireAt", ignore = true)
  Transporter toEntity(TransporterRequest request);

  @InheritConfiguration(name = "toEntity")
  void updateEntity(TransporterRequest request, @MappingTarget Transporter transporter);
}
