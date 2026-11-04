# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ins/e2e/features/address-book/address-book-tabs.spec.ts >> Address book tabs without JavaScript >> following the Branch tab loads it, marks it current and lists the stamped branch address
- Location: tests/ins/e2e/features/address-book/address-book-tabs.spec.ts:109:3

# Error details

```
Error: expect(locator).toHaveCount(expected) failed

Locator:  getByRole('row').filter({ has: getByRole('cell') })
Expected: 1
Received: 4
Timeout:  5000ms

Call log:
  - Expect "toHaveCount" getByRole('row').filter({ has: getByRole('cell') }) with timeout 5000ms
  - waiting for getByRole('row').filter({ has: getByRole('cell') })
    14 × locator resolved to 4 elements
       - unexpected value "4"

```

# Page snapshot

```yaml
- generic [active] [ref=f2e1]:
  - link "Skip to main content" [ref=f2e2] [cursor=pointer]:
    - /url: "#main-content"
  - banner [ref=f2e3]:
    - link [ref=f2e7] [cursor=pointer]:
      - /url: https://www.gov.uk/
      - img "GOV.UK" [ref=f2e8]
    - region "Service information" [ref=f2e21]:
      - generic [ref=f2e23]:
        - link "Import notification service" [ref=f2e25] [cursor=pointer]:
          - /url: /
        - navigation "Menu" [ref=f2e26]:
          - list [ref=f2e27]:
            - listitem [ref=f2e28]:
              - link "Dashboard" [ref=f2e29] [cursor=pointer]:
                - /url: /
            - listitem [ref=f2e30]:
              - link [ref=f2e31] [cursor=pointer]:
                - /url: /address-book
                - strong [ref=f2e32]: Address book
            - listitem [ref=f2e33]:
              - link "Manage account" [ref=f2e34] [cursor=pointer]:
                - /url: "#"
            - listitem [ref=f2e35]:
              - link "Log out" [ref=f2e36] [cursor=pointer]:
                - /url: /auth/sign-out
  - generic [ref=f2e37]:
    - paragraph [ref=f2e39]:
      - strong [ref=f2e40]: Alpha
      - generic [ref=f2e41]:
        - text: This is a new service. Help us improve it and
        - link "give your feedback by email" [ref=f2e42] [cursor=pointer]:
          - /url: mailto:APHAServiceDesk@apha.gov.uk
        - text: .
    - paragraph [ref=f2e44]:
      - strong [ref=f2e45]:
        - generic [ref=f2e46]: "This page’s status:"
        - text: Exploring
        - generic [ref=f2e47]: .
      - generic [ref=f2e48]: This page is an early idea. It may change a lot or be removed.
    - main [ref=f2e49]:
      - generic [ref=f2e51]:
        - heading "Address book" [level=1] [ref=f2e52]
        - paragraph [ref=f2e53]: Manage your addresses for use across all your import notifications
        - button "Add a new address" [ref=f2e55] [cursor=pointer]
        - group [ref=f2e57]:
          - generic "Filter addresses" [ref=f2e58] [cursor=pointer]
          - option "Select one" [selected]
          - option "Branch Address"
        - navigation "Address types" [ref=f2e59]:
          - list [ref=f2e60]:
            - listitem [ref=f2e61]:
              - link "Origin and Consignor" [ref=f2e62] [cursor=pointer]:
                - /url: /address-book?addressType=ORIGIN_OR_CONSIGNOR&q=Stamped+0c8e043214164bcc8b4cca747158c034
            - listitem [ref=f2e63]:
              - link "Destination, Consignee and Importer" [ref=f2e64] [cursor=pointer]:
                - /url: /address-book?addressType=DESTINATION_CONSIGNEE_OR_IMPORTER&q=Stamped+0c8e043214164bcc8b4cca747158c034
            - listitem [ref=f2e65]:
              - link "Branch" [ref=f2e66] [cursor=pointer]:
                - /url: /address-book?addressType=BRANCH&q=Stamped+0c8e043214164bcc8b4cca747158c034
        - heading "Branch addresses" [level=2] [ref=f2e67]
        - paragraph [ref=f2e68]: Showing 1-4 of 4
        - table [ref=f2e69]:
          - rowgroup [ref=f2e70]:
            - row [ref=f2e71]:
              - columnheader "Name" [ref=f2e72]
              - columnheader "Type" [ref=f2e73]
              - columnheader "Address" [ref=f2e74]
              - columnheader "Country" [ref=f2e75]
              - columnheader "Action" [ref=f2e76]
          - rowgroup [ref=f2e78]:
            - row [ref=f2e79]:
              - cell "Stamped 0c8e043214164bcc8b4cca747158c034 Branch" [ref=f2e80]
              - cell "Branch" [ref=f2e81]
              - cell "1 Trader Lane, Carlisle, CA1 3CC" [ref=f2e82]
              - cell "United Kingdom" [ref=f2e83]
              - cell [ref=f2e84]:
                - link "View Stamped 0c8e043214164bcc8b4cca747158c034 Branch" [ref=f2e85] [cursor=pointer]:
                  - /url: /address-book/6aeb4a08d89015ed6115c0bf
                  - text: View
                  - generic [ref=f2e86]: Stamped 0c8e043214164bcc8b4cca747158c034 Branch
            - row [ref=f2e87]:
              - cell "Stamped 0c8e043214164bcc8b4cca747158c034 Importer" [ref=f2e88]
              - cell "Importer" [ref=f2e89]
              - cell "1 Trader Lane, Carlisle, CA1 3CC" [ref=f2e90]
              - cell "United Kingdom" [ref=f2e91]
              - cell [ref=f2e92]:
                - link "View Stamped 0c8e043214164bcc8b4cca747158c034 Importer" [ref=f2e93] [cursor=pointer]:
                  - /url: /address-book/6aeb4a08d89015ed6115c0be
                  - text: View
                  - generic [ref=f2e94]: Stamped 0c8e043214164bcc8b4cca747158c034 Importer
            - row [ref=f2e95]:
              - cell "Stamped 0c8e043214164bcc8b4cca747158c034 Consignor" [ref=f2e96]
              - cell "Consignor" [ref=f2e97]
              - cell "1 Trader Lane, Carlisle, CA1 3CC" [ref=f2e98]
              - cell "United Kingdom" [ref=f2e99]
              - cell [ref=f2e100]:
                - link "View Stamped 0c8e043214164bcc8b4cca747158c034 Consignor" [ref=f2e101] [cursor=pointer]:
                  - /url: /address-book/6aeb4a08d89015ed6115c0bd
                  - text: View
                  - generic [ref=f2e102]: Stamped 0c8e043214164bcc8b4cca747158c034 Consignor
            - row [ref=f2e103]:
              - cell "Stamped 0c8e043214164bcc8b4cca747158c034 Origin And Consignee" [ref=f2e104]
              - cell "Place of origin, Consignee" [ref=f2e105]
              - cell "1 Trader Lane, Carlisle, CA1 3CC" [ref=f2e106]
              - cell "United Kingdom" [ref=f2e107]
              - cell [ref=f2e108]:
                - link "View Stamped 0c8e043214164bcc8b4cca747158c034 Origin And Consignee" [ref=f2e109] [cursor=pointer]:
                  - /url: /address-book/6aeb4a08d89015ed6115c0bc
                  - text: View
                  - generic [ref=f2e110]: Stamped 0c8e043214164bcc8b4cca747158c034 Origin And Consignee
  - contentinfo [ref=f2e111]:
    - generic [ref=f2e124]:
      - generic [ref=f2e125]:
        - heading "Support links" [level=2] [ref=f2e126]
        - list [ref=f2e127]:
          - listitem [ref=f2e128]:
            - link "Privacy" [ref=f2e129] [cursor=pointer]:
              - /url: https://www.gov.uk/help/privacy-notice
          - listitem [ref=f2e130]:
            - link "Cookies" [ref=f2e131] [cursor=pointer]:
              - /url: https://www.gov.uk/help/cookies
          - listitem [ref=f2e132]:
            - link "Accessibility statement" [ref=f2e133] [cursor=pointer]:
              - /url: https://www.gov.uk/help/accessibility-statement
        - generic [ref=f2e136]:
          - text: All content is available under the
          - link "Open Government Licence v3.0" [ref=f2e137] [cursor=pointer]:
            - /url: https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
          - text: ", except where otherwise stated"
      - link "© Crown copyright" [ref=f2e139] [cursor=pointer]:
        - /url: https://www.nationalarchives.gov.uk/information-management/re-using-public-sector-information/uk-government-licensing-framework/crown-copyright/
```

# Test source

```ts
  18  | 
  19  | const createdAddressIds = addressIdsDeletedAfterEachTest('addressBookApi');
  20  | 
  21  | test.describe('Address book tabs API', () => {
  22  |   const isolatedOrganisationAddressIds = addressIdsDeletedAfterEachTest('isolatedOrganisationAddressBookApi');
  23  | 
  24  |   test('listing by an address type returns exactly the addresses holding one of its uses', async ({
  25  |     isolatedOrganisationAddressBookApi,
  26  |   }) => {
  27  |     const { searchTerm, stamped } = await createStamped(isolatedOrganisationAddressBookApi, isolatedOrganisationAddressIds);
  28  | 
  29  |     for (const tab of addressBookTabs) {
  30  |       const listed = await isolatedOrganisationAddressBookApi.listAddresses(searchTerm, { addressType: tab.addressType });
  31  | 
  32  |       expect(listed.map(({ name }) => name).sort()).toEqual(
  33  |         stampedAddressesOnTab(stamped, tab)
  34  |           .map(({ name }) => name)
  35  |           .sort(),
  36  |       );
  37  |     }
  38  |   });
  39  | 
  40  |   test('listing with no address type returns every live address', async ({ isolatedOrganisationAddressBookApi }) => {
  41  |     const { searchTerm, stamped } = await createStamped(isolatedOrganisationAddressBookApi, isolatedOrganisationAddressIds);
  42  | 
  43  |     const listed = await isolatedOrganisationAddressBookApi.listAddresses(searchTerm);
  44  | 
  45  |     expect(listed.map(({ name }) => name).sort()).toEqual(
  46  |       Object.values(stamped)
  47  |         .map(({ name }) => name)
  48  |         .sort(),
  49  |     );
  50  |   });
  51  | });
  52  | 
  53  | test.describe('Address book tabs', () => {
  54  |   test('the three tabs read in order, Origin and Consignor is current on a plain open and on an unknown tab, and Transporter is not offered', async ({
  55  |     insPages,
  56  |   }) => {
  57  |     await insPages.addressBookList.open();
  58  | 
  59  |     await expect(insPages.addressBookList.tabLinks).toHaveText(addressBookTabLabels);
  60  |     await expect(insPages.addressBookList.selectedTab).toHaveText(originOrConsignorTab.label);
  61  | 
  62  |     await insPages.addressBookList.open({ addressType: unsupportedAddressValue as AddressType });
  63  | 
  64  |     await expect(insPages.addressBookList.tabLinks).toHaveText(addressBookTabLabels);
  65  |     await expect(insPages.addressBookList.selectedTab).toHaveText(originOrConsignorTab.label);
  66  |   });
  67  | 
  68  |   test('each tab opens with its own panel heading, the columns Name, Type, Address, Country and Action, and exactly its stamped addresses with their uses', async ({
  69  |     insPages,
  70  |     insJourney,
  71  |     addressBookApi,
  72  |   }) => {
  73  |     const { searchTerm, stamped } = await createStamped(addressBookApi, createdAddressIds);
  74  | 
  75  |     for (const tab of addressBookTabs) {
  76  |       await insJourney.toAddressBookTab(tab, { search: searchTerm });
  77  | 
  78  |       const expected = stampedAddressesOnTab(stamped, tab);
  79  |       await expect(insPages.addressBookList.selectedTab).toHaveText(tab.label);
  80  |       await expect(insPages.addressBookList.panelHeading).toHaveText(tab.panelHeading);
  81  |       await expect(insPages.addressBookList.columnHeaders).toHaveText(['Name', 'Type', 'Address', 'Country', 'Action']);
  82  |       await expect(insPages.addressBookList.resultsLine).toHaveText(`Showing 1-${expected.length} of ${expected.length}`);
  83  |       await expect(insPages.addressBookList.bodyRows).toHaveCount(expected.length);
  84  |       for (const { name, usesLabel } of expected) {
  85  |         await expect(insPages.addressBookList.usesCell(name)).toHaveText(usesLabel);
  86  |       }
  87  |     }
  88  |   });
  89  | 
  90  |   test('every shared fixture shows on the tab of its type with its uses label, and a branch fixture is absent from the Origin and Consignor tab', async ({
  91  |     insPages,
  92  |     insJourney,
  93  |   }) => {
  94  |     for (const { name, addressType, uses } of E2E_ADDRESS_BOOK_FIXTURES) {
  95  |       await insJourney.toAddressBookTab(tabOf(addressType), { search: name });
  96  | 
  97  |       await expect(insPages.addressBookList.usesCell(name)).toHaveText(usesLabelOf(uses));
  98  |     }
  99  | 
  100 |     await insJourney.toAddressBookTab(originOrConsignorTab, { search: branchAddressFixture.name });
  101 | 
  102 |     await expect(insPages.addressBookList.row(branchAddressFixture.name)).toHaveCount(0);
  103 |   });
  104 | });
  105 | 
  106 | test.describe('Address book tabs without JavaScript', () => {
  107 |   test.use({ javaScriptEnabled: false });
  108 | 
  109 |   test('following the Branch tab loads it, marks it current and lists the stamped branch address', async ({ insPages, addressBookApi }) => {
  110 |     const { searchTerm, stamped } = await createStamped(addressBookApi, createdAddressIds);
  111 |     await insPages.addressBookList.open();
  112 | 
  113 |     await insPages.addressBookList.tabLink(branchTab.label).click();
  114 |     await insPages.addressBookList.search(searchTerm, { submitWith: 'button' });
  115 | 
  116 |     await expect(insPages.addressBookList.selectedTab).toHaveText(branchTab.label);
  117 |     await expect(insPages.addressBookList.panelHeading).toHaveText(branchTab.panelHeading);
> 118 |     await expect(insPages.addressBookList.bodyRows).toHaveCount(1);
      |                                                     ^ Error: expect(locator).toHaveCount(expected) failed
  119 |     await expect(insPages.addressBookList.row(stamped.branchAddress.name)).toBeVisible();
  120 |   });
  121 | });
  122 | 
  123 | test.describe('Address book fixture reconcile', () => {
  124 |   test('a fixture record holding a different use is rewritten with the fixture type and uses once, and a second run leaves it untouched', async ({
  125 |     isolatedOrganisationAddressBookApi,
  126 |   }) => {
  127 |     const { name, addressLine1, townOrCity, postcode, countryCode, phone, email } = placeOfOriginFixture;
  128 |     await isolatedOrganisationAddressBookApi.createAddress({
  129 |       name,
  130 |       addressLine1,
  131 |       townOrCity,
  132 |       postcode,
  133 |       countryCode,
  134 |       phone,
  135 |       email,
  136 |       ...heldAsConsignor,
  137 |     });
  138 | 
  139 |     await ensureE2eAddressBook(isolatedOrganisationAddressBookApi);
  140 | 
  141 |     const afterFirstRun = await isolatedOrganisationAddressBookApi.findByName(name);
  142 |     expect(afterFirstRun.addressType).toBe(placeOfOriginFixture.addressType);
  143 |     expect(afterFirstRun.uses).toEqual(placeOfOriginFixture.uses);
  144 | 
  145 |     await ensureE2eAddressBook(isolatedOrganisationAddressBookApi);
  146 | 
  147 |     const afterSecondRun = await isolatedOrganisationAddressBookApi.findByName(name);
  148 |     expect(afterSecondRun.modifiedAt).toBe(afterFirstRun.modifiedAt);
  149 |   });
  150 | });
  151 | 
```