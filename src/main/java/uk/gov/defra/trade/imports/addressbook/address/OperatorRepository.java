package uk.gov.defra.trade.imports.addressbook.address;

import java.util.Optional;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface OperatorRepository extends Repository<Address, String>, OperatorListQuery {

  Address save(Address address);

  Optional<Address> findByIdAndOrganisationId(String id, String organisationId);
}
