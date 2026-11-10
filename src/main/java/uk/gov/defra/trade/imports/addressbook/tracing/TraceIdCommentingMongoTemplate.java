package uk.gov.defra.trade.imports.addressbook.tracing;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

/**
 * A {@link MongoTemplate} whose every collection is a {@link TraceIdCommentedCollection}, so the
 * commands it covers carry the MDC's {@code trace.id} as their comment.
 */
public class TraceIdCommentingMongoTemplate extends MongoTemplate {

  /**
   * Creates the template.
   *
   * @param mongoDatabaseFactory the factory for the database the template runs against
   * @param mongoConverter the converter between documents and entities
   */
  public TraceIdCommentingMongoTemplate(
      MongoDatabaseFactory mongoDatabaseFactory, MongoConverter mongoConverter) {
    super(mongoDatabaseFactory, mongoConverter);
  }

  @Override
  protected MongoCollection<Document> prepareCollection(MongoCollection<Document> collection) {
    return TraceIdCommentedCollection.of(super.prepareCollection(collection));
  }
}
