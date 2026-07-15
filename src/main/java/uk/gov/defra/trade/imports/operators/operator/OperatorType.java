package uk.gov.defra.trade.imports.operators.operator;

/**
 * Operator type. The constant order is the EUDPA-186.AC2 / EUDPA-287.AC1 UI order and is
 * load-bearing for radio/select rendering; the UI renders a visual 'or' divider before
 * {@code BRANCH_ADDRESS}.
 */
public enum OperatorType {
  PLACE_OF_ORIGIN,
  CONSIGNOR,
  CONSIGNEE,
  IMPORTER,
  PLACE_OF_DESTINATION,
  TRANSPORTER,
  BRANCH_ADDRESS
}
