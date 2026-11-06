package uk.gov.defra.trade.imports.addressbook.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonValue;

final class MongoReplyWriteException extends RuntimeException {

  private MongoReplyWriteException(String errorCodes) {
    super(errorCodes, null, false, false);
  }

  static Optional<MongoReplyWriteException> reportedBy(BsonDocument reply) {
    List<String> writeFailures = new ArrayList<>();
    if (reply.isArray("writeErrors") && !reply.getArray("writeErrors").isEmpty()) {
      List<String> codes =
          reply.getArray("writeErrors").stream()
              .map(BsonValue::asDocument)
              .map(MongoReplyWriteException::errorCodeOf)
              .toList();
      writeFailures.add("writeErrors codes " + codes);
    }
    if (reply.isDocument("writeConcernError")) {
      writeFailures.add(
          "writeConcernError code " + errorCodeOf(reply.getDocument("writeConcernError")));
    }
    return writeFailures.isEmpty()
        ? Optional.empty()
        : Optional.of(new MongoReplyWriteException(String.join("; ", writeFailures)));
  }

  private static String errorCodeOf(BsonDocument error) {
    String code = String.valueOf(error.getNumber("code").intValue());
    return error.isString("codeName") ? code + " " + error.getString("codeName").getValue() : code;
  }
}
