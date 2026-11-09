package uk.gov.defra.trade.imports.addressbook.transporter;

import java.util.Optional;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface TransporterRepository
    extends Repository<Transporter, String>, TransporterListQuery {

  Transporter save(Transporter transporter);

  Optional<Transporter> findByIdAndOrganisationId(String id, String organisationId);
}
