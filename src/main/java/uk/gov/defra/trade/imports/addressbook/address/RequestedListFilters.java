package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The filters of a list request, exactly as the request supplied them. Every component may be null.
 *
 * @param searchTerm the user's search term as typed
 * @param countryCodes the requested ISO alpha-2 country codes
 * @param category the address type whose uses the listing filters by
 * @param use the one use an address must hold
 * @param searchUses the uses that make an address match the search
 */
public record RequestedListFilters(
    @Nullable String searchTerm,
    @Nullable List<String> countryCodes,
    @Nullable AddressType category,
    @Nullable AddressUse use,
    @Nullable List<AddressUse> searchUses) {}
