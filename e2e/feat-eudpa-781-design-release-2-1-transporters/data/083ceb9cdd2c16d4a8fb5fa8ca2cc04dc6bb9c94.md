# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ins/e2e/features/address-book/address-book-add-type.spec.ts >> Choosing an address type before adding an address >> Add a new address opens Choose an address type with its caption, the four types and their hints in order, Continue and Back to the address book
- Location: tests/ins/e2e/features/address-book/address-book-add-type.spec.ts:25:3

# Error details

```
Error: expect(locator).toHaveCount(expected) failed

Locator:  getByRole('group', { name: 'Choose an address type' }).getByRole('radio')
Expected: 4
Received: 3
Timeout:  5000ms

Call log:
  - Expect "toHaveCount" getByRole('group', { name: 'Choose an address type' }).getByRole('radio') with timeout 5000ms
  - waiting for getByRole('group', { name: 'Choose an address type' }).getByRole('radio')
    14 × locator resolved to 3 elements
       - unexpected value "3"

```

# Page snapshot

```yaml
- generic [active] [ref=f1e1]:
  - link "Skip to main content" [ref=f1e2] [cursor=pointer]:
    - /url: "#main-content"
  - banner [ref=f1e3]:
    - link [ref=f1e7] [cursor=pointer]:
      - /url: https://www.gov.uk/
      - img "GOV.UK" [ref=f1e8]
    - region "Service information" [ref=f1e21]:
      - generic [ref=f1e23]:
        - link "Import notification service" [ref=f1e25] [cursor=pointer]:
          - /url: /
        - navigation "Menu" [ref=f1e26]:
          - list [ref=f1e27]:
            - listitem [ref=f1e28]:
              - link "Dashboard" [ref=f1e29] [cursor=pointer]:
                - /url: /
            - listitem [ref=f1e30]:
              - link [ref=f1e31] [cursor=pointer]:
                - /url: /address-book
                - strong [ref=f1e32]: Address book
            - listitem [ref=f1e33]:
              - link "Manage account" [ref=f1e34] [cursor=pointer]:
                - /url: "#"
            - listitem [ref=f1e35]:
              - link "Log out" [ref=f1e36] [cursor=pointer]:
                - /url: /auth/sign-out
  - generic [ref=f1e37]:
    - paragraph [ref=f1e39]:
      - strong [ref=f1e40]: Alpha
      - generic [ref=f1e41]:
        - text: This is a new service. Help us improve it and
        - link "give your feedback by email" [ref=f1e42] [cursor=pointer]:
          - /url: mailto:APHAServiceDesk@apha.gov.uk
        - text: .
    - paragraph [ref=f1e44]:
      - strong [ref=f1e45]:
        - generic [ref=f1e46]: "This page’s status:"
        - text: Exploring
        - generic [ref=f1e47]: .
      - generic [ref=f1e48]: This page is an early idea. It may change a lot or be removed.
    - link "Back" [ref=f1e49] [cursor=pointer]:
      - /url: /address-book
    - main [ref=f1e50]:
      - generic [ref=f1e52]:
        - generic [ref=f1e53]: Add a new address
        - heading "Choose an address type" [level=1] [ref=f1e54]
        - generic [ref=f1e55]:
          - group "Choose an address type" [ref=f1e57]:
            - generic [ref=f1e59]:
              - generic [ref=f1e60]:
                - radio "Origin or Consignor" [ref=f1e61] [cursor=pointer]
                - generic [ref=f1e62] [cursor=pointer]: Origin or Consignor
                - generic [ref=f1e63]: Place of origin, Consignor
              - generic [ref=f1e64]:
                - radio "Destination, Consignee or Importer" [ref=f1e65] [cursor=pointer]
                - generic [ref=f1e66] [cursor=pointer]: Destination, Consignee or Importer
                - generic [ref=f1e67]: Place of destination, Consignee, Importer
              - generic [ref=f1e68]:
                - radio "Branch" [ref=f1e69] [cursor=pointer]
                - generic [ref=f1e70] [cursor=pointer]: Branch
                - generic [ref=f1e71]: Branch address
          - button "Continue" [ref=f1e72] [cursor=pointer]
  - contentinfo [ref=f1e73]:
    - generic [ref=f1e86]:
      - generic [ref=f1e87]:
        - heading "Support links" [level=2] [ref=f1e88]
        - list [ref=f1e89]:
          - listitem [ref=f1e90]:
            - link "Privacy" [ref=f1e91] [cursor=pointer]:
              - /url: https://www.gov.uk/help/privacy-notice
          - listitem [ref=f1e92]:
            - link "Cookies" [ref=f1e93] [cursor=pointer]:
              - /url: https://www.gov.uk/help/cookies
          - listitem [ref=f1e94]:
            - link "Accessibility statement" [ref=f1e95] [cursor=pointer]:
              - /url: https://www.gov.uk/help/accessibility-statement
        - generic [ref=f1e98]:
          - text: All content is available under the
          - link "Open Government Licence v3.0" [ref=f1e99] [cursor=pointer]:
            - /url: https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
          - text: ", except where otherwise stated"
      - link "© Crown copyright" [ref=f1e101] [cursor=pointer]:
        - /url: https://www.nationalarchives.gov.uk/information-management/re-using-public-sector-information/uk-government-licensing-framework/crown-copyright/
```

# Test source

```ts
  1   | import { escapeRegex } from '@page-objects/shared/escape-regex';
  2   | import { test, expect } from '@fixtures';
  3   | import { addressTypes, addressRoles, addressTypesAskingRoles, type AddressTypeAskingRoles } from '@domain/shared/constants/address-types';
  4   | import { addressTypeOptions, addressTypeQuestion } from '@domain/ins/constants/address-book-types';
  5   | import { addFormAddressWithLineTwoAndCounty } from '@domain/ins/constants/trader-address';
  6   | import { ADDRESS_FORM_TEXTBOX_LABELS_IN_ORDER } from '@domain/ins/constants/address-form';
  7   | import type { InsAddressBookAddressTypePage } from '@page-objects/ins/address-book/address-type-page';
  8   | import type { InsAddressBookFormPage } from '@page-objects/ins/address-book/form-page';
  9   | import { expectAddressBookList } from './expect-address-book-list';
  10  | 
  11  | const expectNoTypeChosen = async (typePage: InsAddressBookAddressTypePage): Promise<void> => {
  12  |   for (const { addressType } of addressTypeOptions) {
  13  |     await expect(typePage.radio(addressType)).not.toBeChecked();
  14  |   }
  15  | };
  16  | 
  17  | const expectTextboxesEmpty = async (form: InsAddressBookFormPage): Promise<void> => {
  18  |   await expect(form.textboxes).toHaveCount(ADDRESS_FORM_TEXTBOX_LABELS_IN_ORDER.length);
  19  |   for (const textbox of await form.textboxes.all()) {
  20  |     await expect(textbox).toHaveValue('');
  21  |   }
  22  | };
  23  | 
  24  | test.describe('Choosing an address type before adding an address', () => {
  25  |   test('Add a new address opens Choose an address type with its caption, the four types and their hints in order, Continue and Back to the address book', async ({
  26  |     pages,
  27  |     insPages,
  28  |     insJourney,
  29  |   }) => {
  30  |     await insJourney.toAddressTypeFromList();
  31  | 
  32  |     const typePage = insPages.addressBookAddressType;
  33  |     await expect(pages.page).toHaveURL(new RegExp(`${escapeRegex(typePage.expectedUrl)}\\?start=true$`));
  34  |     await expect(typePage.caption).toBeVisible();
  35  |     await expect(typePage.heading).toBeVisible();
> 36  |     await expect(typePage.radios).toHaveCount(addressTypeOptions.length);
      |                                   ^ Error: expect(locator).toHaveCount(expected) failed
  37  |     for (const [index, { label, hint }] of addressTypeOptions.entries()) {
  38  |       await expect(typePage.radios.nth(index)).toHaveAccessibleName(label);
  39  |       await expect(typePage.radios.nth(index)).toHaveAccessibleDescription(hint);
  40  |     }
  41  |     await expect(typePage.btnContinue).toBeVisible();
  42  | 
  43  |     await typePage.linkBack.click();
  44  | 
  45  |     await expectAddressBookList({ pages, insPages });
  46  |   });
  47  | 
  48  |   test('Continue with no type chosen shows Select an address type against the options and in the summary, whose link focuses the first option', async ({
  49  |     insPages,
  50  |     insJourney,
  51  |   }) => {
  52  |     const typePage = insPages.addressBookAddressType;
  53  |     await insJourney.toAddressType();
  54  | 
  55  |     await typePage.continue();
  56  | 
  57  |     await expect(typePage.errorSummary).toBeVisible();
  58  |     await expect(typePage.radioGroup).toHaveAccessibleDescription(`Error: ${addressTypeQuestion.error}`);
  59  |     await typePage.errorSummaryLink(addressTypeQuestion.error).click();
  60  |     await expect(typePage.radio(addressTypes.originOrConsignor)).toBeFocused();
  61  |   });
  62  | 
  63  |   const opensAddForm = (option: (typeof addressTypeOptions)[number]): option is typeof option & { addressType: AddressTypeAskingRoles } =>
  64  |     (addressTypesAskingRoles as readonly string[]).includes(option.addressType);
  65  | 
  66  |   const addFormOptions = addressTypeOptions.filter(opensAddForm);
  67  | 
  68  |   for (const { addressType, label } of addFormOptions) {
  69  |     test(`choosing ${label} opens the add form with its caption and heading and no status tag, and Back shows the type chosen`, async ({
  70  |       insPages,
  71  |       insJourney,
  72  |     }) => {
  73  |       await insJourney.toAddressForm({ addressType });
  74  | 
  75  |       await expect(insPages.addressBookAdd.caption).toBeVisible();
  76  |       await expect(insPages.addressBookAdd.heading).toBeVisible();
  77  |       await expect(insPages.addressBookAdd.statusTag).toHaveCount(0);
  78  | 
  79  |       await insPages.addressBookAdd.linkBack.click();
  80  | 
  81  |       await expect(insPages.addressBookAddressType.radio(addressType)).toBeChecked();
  82  |     });
  83  |   }
  84  | 
  85  |   const roleTickedByAddressType = {
  86  |     [addressTypes.originOrConsignor]: addressRoles.placeOfOrigin,
  87  |     [addressTypes.destinationConsigneeOrImporter]: addressRoles.consignee,
  88  |     [addressTypes.branch]: addressRoles.branchAddress,
  89  |   } as const;
  90  | 
  91  |   for (const { addressType, label } of addFormOptions) {
  92  |     test(`after a refused save of ${label}, choosing it again shows the address, the contact details and the ticked role entered`, async ({
  93  |       insPages,
  94  |       insJourney,
  95  |     }) => {
  96  |       const addPage = insPages.addressBookAdd;
  97  |       const role = roleTickedByAddressType[addressType];
  98  |       const entered = {
  99  |         ...addFormAddressWithLineTwoAndCounty(`Kept Entry ${label} ${Date.now()}`, 'United Kingdom'),
  100 |         email: 'not-an-email',
  101 |       };
  102 |       await insJourney.toAddressForm({ addressType });
  103 |       await addPage.fill(entered);
  104 |       await addPage.tickRoles([role]);
  105 |       await addPage.saveAndContinue();
  106 |       await expect(addPage.errorSummary).toBeVisible();
  107 | 
  108 |       await addPage.linkBack.click();
  109 |       await insJourney.chooseAddressType(addressType);
  110 | 
  111 |       await expect(addPage.inputName).toHaveValue(entered.name);
  112 |       await expect(addPage.inputAddressLine1).toHaveValue(entered.addressLine1);
  113 |       await expect(addPage.inputAddressLine2).toHaveValue(entered.addressLine2);
  114 |       await expect(addPage.inputTownOrCity).toHaveValue(entered.townOrCity);
  115 |       await expect(addPage.inputCounty).toHaveValue(entered.county);
  116 |       await expect(addPage.inputPostcode).toHaveValue(entered.postcode);
  117 |       await expect(addPage.selectedCountryOption).toHaveText(entered.country);
  118 |       await expect(addPage.inputEmail).toHaveValue(entered.email);
  119 |       await expect(addPage.inputPhone).toHaveValue(entered.phone);
  120 |       await expect(addPage.rolesOption(role)).toBeChecked();
  121 |     });
  122 |   }
  123 | 
  124 |   test('Add a new address from the address book list again starts with no type chosen, empty text fields and no role ticked', async ({
  125 |     insPages,
  126 |     insJourney,
  127 |   }) => {
  128 |     const addPage = insPages.addressBookAdd;
  129 |     await insJourney.toAddressFormFromList({ addressType: addressTypes.branch });
  130 |     await addPage.fill(addFormAddressWithLineTwoAndCounty(`Abandoned Entry ${Date.now()}`, 'United Kingdom'));
  131 |     await addPage.tickRoles([addressRoles.branchAddress]);
  132 |     await insPages.addressBookList.open();
  133 | 
  134 |     await insPages.addressBookList.btnAddNewAddress.click();
  135 | 
  136 |     await expectNoTypeChosen(insPages.addressBookAddressType);
```