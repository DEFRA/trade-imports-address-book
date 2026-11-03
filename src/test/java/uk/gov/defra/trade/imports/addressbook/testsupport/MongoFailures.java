package uk.gov.defra.trade.imports.addressbook.testsupport;

import com.mongodb.MongoSocketReadException;
import com.mongodb.ServerAddress;

public final class MongoFailures {

  public static final String CONNECTION_RESET = "connection reset";

  private MongoFailures() {}

  public static MongoSocketReadException connectionReset() {
    return new MongoSocketReadException(CONNECTION_RESET, new ServerAddress());
  }
}
