# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ins/e2e/features/address-book/address-book-type-filter.spec.ts >> Address book use filter >> the tab stays selected after a use filter is submitted, and following another tab keeps the search, resets the use filter and lists that tab
- Location: tests/ins/e2e/features/address-book/address-book-type-filter.spec.ts:171:3

# Error details

```
Error: expect(locator).toHaveText(expected) failed

Locator: getByTestId('address-book-results-label')
Timeout: 5000ms
- Expected  - 1
+ Received  + 3

- Showing 1-2 of 2
+
+       Showing 1-4 of 4
+     

Call log:
  - Expect "toHaveText" getByTestId('address-book-results-label') with timeout 5000ms
  - waiting for getByTestId('address-book-results-label')
    14 × locator resolved to <p data-testid="address-book-results-label" class="govuk-body govuk-!-margin-bottom-4">↵      Showing 1-4 of 4↵    </p>
       - unexpected value "
      Showing 1-4 of 4
    "

```

```yaml
- paragraph: Showing 1-4 of 4
```

# Test source

```ts
  91  |     await expect(insPages.addressBookList.pagination).toHaveCount(0);
  92  |   });
  93  | 
  94  |   test('a use filter that runs past one page is counted and paged, and the next page keeps the tab, the search and the use filter', async ({
  95  |     insPages,
  96  |     addressBookApi,
  97  |     page,
  98  |   }) => {
  99  |     const term = useFilterTermOf('paged');
  100 |     for (let index = 1; index <= 9; index++) {
  101 |       await createAddressHolding(addressBookApi, createdAddressIds, {
  102 |         name: `${term} Consignor ${index}`,
  103 |         addressType: addressTypes.originOrConsignor,
  104 |         uses: [addressUses.consignor],
  105 |       });
  106 |     }
  107 |     await insPages.addressBookList.open();
  108 | 
  109 |     await insPages.addressBookList.filter({ term, submitWith: 'button', use: addressUses.consignor });
  110 | 
  111 |     await expect(insPages.addressBookList.resultsLine).toHaveText('Showing 1-8 of 9');
  112 |     const href = await insPages.addressBookList.linkNextPage.getAttribute('href');
  113 |     const next = new URL(href ?? '', page.url());
  114 |     expect(next.searchParams.get('addressType')).toBe(addressTypes.originOrConsignor);
  115 |     expect(next.searchParams.get('q')).toBe(term);
  116 |     expect(next.searchParams.get('use')).toBe(addressUses.consignor);
  117 |     expect(next.searchParams.get('page')).toBe('2');
  118 | 
  119 |     await insPages.addressBookList.linkNextPage.click();
  120 | 
  121 |     await expect(insPages.addressBookList.resultsLine).toHaveText('Showing 9-9 of 9');
  122 |     await expect(insPages.addressBookList.selectedTab).toHaveText(originOrConsignorTab.label);
  123 |     await expect(insPages.addressBookList.selectedUseFilterOption).toHaveText(useFilterOptionTexts.CONSIGNOR);
  124 |   });
  125 | 
  126 |   test('a use filter matching one address counts one', async ({ insPages, addressBookApi }) => {
  127 |     const term = useFilterTermOf('counts one');
  128 |     await createAddressHolding(addressBookApi, createdAddressIds, {
  129 |       name: `${term} Consignor`,
  130 |       addressType: addressTypes.originOrConsignor,
  131 |       uses: [addressUses.consignor],
  132 |     });
  133 |     await createAddressHolding(addressBookApi, createdAddressIds, {
  134 |       name: `${term} Place Of Origin`,
  135 |       addressType: addressTypes.originOrConsignor,
  136 |       uses: [addressUses.placeOfOrigin],
  137 |     });
  138 |     await insPages.addressBookList.open();
  139 | 
  140 |     await insPages.addressBookList.filter({ term, submitWith: 'button', use: addressUses.placeOfOrigin });
  141 | 
  142 |     await expect(insPages.addressBookList.resultsLine).toHaveText('Showing 1-1 of 1');
  143 |   });
  144 | 
  145 |   test('a use filter chosen and not submitted leaves the URL, the results line and the listed rows unchanged', async ({
  146 |     insPages,
  147 |     addressBookApi,
  148 |     page,
  149 |   }) => {
  150 |     const stamp = Date.now();
  151 |     const stamped = await createStamped(addressBookApi, stamp, createdAddressIds);
  152 |     await insPages.addressBookList.open();
  153 |     await insPages.addressBookList.search(stampedSearchTermOf(stamp), { submitWith: 'button' });
  154 |     const originAddresses = stampedAddressesOnTab(stamped, originOrConsignorTab);
  155 |     const resultsLine = `Showing 1-${originAddresses.length} of ${originAddresses.length}`;
  156 |     await expect(insPages.addressBookList.resultsLine).toHaveText(resultsLine);
  157 |     const urlBeforeChoosing = page.url();
  158 | 
  159 |     await insPages.addressBookList.chooseUseFilter(addressUses.consignor);
  160 | 
  161 |     await expect(insPages.addressBookList.selectedUseFilterOption).toHaveText(useFilterOptionTexts.CONSIGNOR);
  162 |     await expect(page).toHaveURL(urlBeforeChoosing);
  163 |     expect(new URL(page.url()).searchParams.get('use')).toBe('');
  164 |     await expect(insPages.addressBookList.resultsLine).toHaveText(resultsLine);
  165 |     await expect(insPages.addressBookList.bodyRows).toHaveCount(originAddresses.length);
  166 |     for (const { name } of originAddresses) {
  167 |       await expect(insPages.addressBookList.row(name)).toBeVisible();
  168 |     }
  169 |   });
  170 | 
  171 |   test('the tab stays selected after a use filter is submitted, and following another tab keeps the search, resets the use filter and lists that tab', async ({
  172 |     insPages,
  173 |     addressBookApi,
  174 |   }) => {
  175 |     const { stamp, stamped } = await createStampedAndFilterListByUse(
  176 |       { insPages, api: addressBookApi, createdAddressIds },
  177 |       addressUses.consignor,
  178 |     );
  179 | 
  180 |     await expect(insPages.addressBookList.selectedTab).toHaveText(originOrConsignorTab.label);
  181 |     await expect(insPages.addressBookList.bodyRows).toHaveCount(1);
  182 |     await expect(insPages.addressBookList.row(stamped.consignor.name)).toBeVisible();
  183 | 
  184 |     await insPages.addressBookList.followTab(destinationConsigneeOrImporterTab);
  185 | 
  186 |     const destinationAddresses = stampedAddressesOnTab(stamped, destinationConsigneeOrImporterTab);
  187 |     await expect(insPages.addressBookList.selectedTab).toHaveText(destinationConsigneeOrImporterTab.label);
  188 |     await insPages.addressBookList.filterSummary.click();
  189 |     await expect(insPages.addressBookList.inputSearch).toHaveValue(stampedSearchTermOf(stamp));
  190 |     await expect(insPages.addressBookList.selectedUseFilterOption).toHaveText(useFilterPlaceholder);
> 191 |     await expect(insPages.addressBookList.resultsLine).toHaveText(
      |                                                        ^ Error: expect(locator).toHaveText(expected) failed
  192 |       `Showing 1-${destinationAddresses.length} of ${destinationAddresses.length}`,
  193 |     );
  194 |     for (const { name, usesLabel } of destinationAddresses) {
  195 |       await expect(insPages.addressBookList.usesCell(name)).toHaveText(usesLabel);
  196 |     }
  197 |   });
  198 | 
  199 |   test('a search for a use label with a use filter chosen lists the address holding that use, found on whichever result page it falls', async ({
  200 |     insPages,
  201 |     addressBookApi,
  202 |   }) => {
  203 |     const name = `Label Search ${Date.now()}`;
  204 |     await createAddressHolding(addressBookApi, createdAddressIds, {
  205 |       name,
  206 |       addressType: addressTypes.originOrConsignor,
  207 |       uses: [addressUses.placeOfOrigin, addressUses.consignor],
  208 |     });
  209 | 
  210 |     await expect(async () => {
  211 |       await insPages.addressBookList.open();
  212 |       await insPages.addressBookList.filter({ term: 'consignor', submitWith: 'button', use: addressUses.placeOfOrigin });
  213 | 
  214 |       expect(await insPages.addressBookList.followResultPagesUntilRowShows(name)).toBe(true);
  215 |     }).toPass({ timeout: timeouts.medium });
  216 | 
  217 |     await expect(insPages.addressBookList.usesCell(name)).toHaveText(usesLabelOf([addressUses.placeOfOrigin, addressUses.consignor]));
  218 |   });
  219 | });
  220 | 
  221 | test.describe('Address book use filter without JavaScript', () => {
  222 |   test.use({ javaScriptEnabled: false });
  223 | 
  224 |   test('a use filter chosen and submitted with the Search button lists only the addresses holding it', async ({
  225 |     insPages,
  226 |     addressBookApi,
  227 |   }) => {
  228 |     const { stamped } = await createStampedAndFilterListByUse({ insPages, api: addressBookApi, createdAddressIds }, addressUses.consignor);
  229 | 
  230 |     await expect(insPages.addressBookList.resultsLine).toHaveText('Showing 1-1 of 1');
  231 |     await expect(insPages.addressBookList.bodyRows).toHaveCount(1);
  232 |     await expect(insPages.addressBookList.row(stamped.consignor.name)).toBeVisible();
  233 |   });
  234 | });
  235 | 
```