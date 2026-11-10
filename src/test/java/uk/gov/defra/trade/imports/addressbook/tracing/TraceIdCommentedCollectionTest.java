package uk.gov.defra.trade.imports.addressbook.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.MongoNamespace;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import uk.gov.defra.trade.imports.addressbook.filter.RequestTracingFilter;

class TraceIdCommentedCollectionTest {

  private static final String TRACE_ID = "trace-from-the-caller";
  private static final Document FILTER = new Document("organisationId", "org-1");
  private static final Document ADDRESS = new Document("name", "Farm");

  private MongoCollection<Document> target;
  private MongoCollection<Document> collection;

  @BeforeEach
  void wrapACollection() {
    target = mockCollection();
    collection = TraceIdCommentedCollection.of(target);
  }

  @AfterEach
  void clearTheMdc() {
    MDC.clear();
  }

  @Test
  void find_sendsTheTraceIdAsTheComment() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    FindIterable<Document> find = findReturnedFor(target);

    collection.find(FILTER, Document.class);

    verify(find).comment(TRACE_ID);
  }

  @Test
  void countDocuments_setsTheTraceIdOnTheOptionsItIsGiven() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    CountOptions options = new CountOptions();

    collection.countDocuments(FILTER, options);

    assertThat(options.getComment()).isEqualTo(new BsonString(TRACE_ID));
    verify(target).countDocuments(FILTER, options);
  }

  @Test
  void insertOne_withNoOptions_sendsTheTraceIdInInsertOneOptions() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);

    collection.insertOne(ADDRESS);

    assertThat(insertOneOptionsSentTo(target).getComment()).isEqualTo(new BsonString(TRACE_ID));
  }

  @Test
  void insertOne_setsTheTraceIdOnTheOptionsItIsGiven() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    InsertOneOptions options = new InsertOneOptions().bypassDocumentValidation(true);

    collection.insertOne(ADDRESS, options);

    assertThat(options.getComment()).isEqualTo(new BsonString(TRACE_ID));
    assertThat(options.getBypassDocumentValidation()).isTrue();
    verify(target).insertOne(ADDRESS, options);
  }

  @Test
  void aMethodWithNoCommandToComment_runsUnchanged() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    MongoNamespace namespace = new MongoNamespace("trade-imports-address-book", "address");
    when(target.getNamespace()).thenReturn(namespace);

    assertThat(collection.getNamespace()).isSameAs(namespace);
  }

  @Test
  void replaceOne_setsTheTraceIdOnTheOptionsItIsGiven() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    ReplaceOptions options = new ReplaceOptions().upsert(true);

    collection.replaceOne(FILTER, ADDRESS, options);

    assertThat(options.getComment()).isEqualTo(new BsonString(TRACE_ID));
    assertThat(options.isUpsert()).isTrue();
    verify(target).replaceOne(FILTER, ADDRESS, options);
  }

  @Test
  void aCollectionMadeWithAWriteConcern_alsoSendsTheTraceId() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    MongoCollection<Document> majority = mockCollection();
    when(target.withWriteConcern(WriteConcern.MAJORITY)).thenReturn(majority);

    collection.withWriteConcern(WriteConcern.MAJORITY).insertOne(ADDRESS);

    assertThat(insertOneOptionsSentTo(majority).getComment())
        .isEqualTo(new BsonString(TRACE_ID));
  }

  @Test
  void withNoTraceId_runsEachCommandUnchanged() {
    FindIterable<Document> find = findReturnedFor(target);
    CountOptions options = new CountOptions();

    collection.find(FILTER, Document.class);
    collection.countDocuments(FILTER, options);
    collection.insertOne(ADDRESS);

    verify(find, never()).comment(anyString());
    assertThat(options.getComment()).isNull();
    verify(target).insertOne(ADDRESS);
  }

  @Test
  void aDriverFailureReachesTheCallerAsTheDriverThrewIt() {
    MDC.put(RequestTracingFilter.MDC_TRACE_ID, TRACE_ID);
    MongoException insertFailure = new MongoException("closed");
    MongoException countFailure = new MongoException("timed out");
    when(target.insertOne(eq(ADDRESS), any(InsertOneOptions.class))).thenThrow(insertFailure);
    when(target.countDocuments(eq(FILTER), any(CountOptions.class))).thenThrow(countFailure);

    assertThatExceptionOfType(MongoException.class)
        .isThrownBy(() -> collection.insertOne(ADDRESS))
        .isSameAs(insertFailure);
    assertThatExceptionOfType(MongoException.class)
        .isThrownBy(() -> collection.countDocuments(FILTER, new CountOptions()))
        .isSameAs(countFailure);
  }

  @Test
  void aDriverErrorReachesTheCallerAsTheDriverThrewIt() {
    LinkageError failure = new LinkageError("codec missing");
    when(target.find(FILTER, Document.class)).thenThrow(failure);

    assertThatExceptionOfType(LinkageError.class)
        .isThrownBy(() -> collection.find(FILTER, Document.class))
        .isSameAs(failure);
  }

  @SuppressWarnings("unchecked")
  private static MongoCollection<Document> mockCollection() {
    return mock(MongoCollection.class);
  }

  @SuppressWarnings("unchecked")
  private static FindIterable<Document> findReturnedFor(MongoCollection<Document> collection) {
    FindIterable<Document> find = mock(FindIterable.class);
    when(collection.find(FILTER, Document.class)).thenReturn(find);
    when(find.comment(anyString())).thenReturn(find);
    return find;
  }

  private static InsertOneOptions insertOneOptionsSentTo(MongoCollection<Document> collection) {
    ArgumentCaptor<InsertOneOptions> options = ArgumentCaptor.forClass(InsertOneOptions.class);
    verify(collection).insertOne(eq(ADDRESS), options.capture());
    return options.getValue();
  }
}
