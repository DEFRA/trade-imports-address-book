package uk.gov.defra.trade.imports.addressbook;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Builds the {@link Page} a repository mock answers a list call with, in the tests this package shares. */
public final class PageTestSupport {

  private PageTestSupport() {}

  public static <T> Page<T> pageOf(List<T> items, int pageIndex, int pageSize, long total) {
    return new PageImpl<>(items, PageRequest.of(pageIndex, pageSize), total);
  }
}
