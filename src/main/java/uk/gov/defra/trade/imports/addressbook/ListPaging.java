package uk.gov.defra.trade.imports.addressbook;

import uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException;

/** The page-size bounds every address-book list shares. */
public final class ListPaging {

  public static final int MIN_PAGE_SIZE = 1;
  public static final int MAX_PAGE_SIZE = 100;
  public static final int DEFAULT_PAGE_SIZE = 25;

  private ListPaging() {}

  /**
   * @param page the 1-based page number
   * @throws BadRequestException if {@code page} is less than 1
   */
  public static void requirePositivePage(int page) {
    if (page < 1) {
      throw new BadRequestException("page must be 1 or greater");
    }
  }

  /**
   * @param requestedPageSize the page size from {@link #MIN_PAGE_SIZE} to {@link #MAX_PAGE_SIZE},
   *     or null for {@link #DEFAULT_PAGE_SIZE}
   * @return the page size to serve
   * @throws BadRequestException if {@code requestedPageSize} is outside {@link #MIN_PAGE_SIZE} to
   *     {@link #MAX_PAGE_SIZE}
   */
  public static int servedPageSize(Integer requestedPageSize) {
    if (requestedPageSize == null) {
      return DEFAULT_PAGE_SIZE;
    }
    if (requestedPageSize < MIN_PAGE_SIZE || requestedPageSize > MAX_PAGE_SIZE) {
      throw new BadRequestException(
          "pageSize must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
    }
    return requestedPageSize;
  }
}
