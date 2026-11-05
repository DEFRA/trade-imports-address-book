package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The filters of a list request, exactly as the request supplied them. Every component may be null.
 *
 * @param searchTerm the user's search term as typed
 * @param countryCodes the requested ISO alpha-2 country codes
 * @param roles the requested roles to keep
 * @param searchRoles the requested search roles
 */
public record RequestedListFilters(
    @Nullable String searchTerm,
    @Nullable List<String> countryCodes,
    @Nullable List<AddressRole> roles,
    @Nullable List<AddressRole> searchRoles) {}
