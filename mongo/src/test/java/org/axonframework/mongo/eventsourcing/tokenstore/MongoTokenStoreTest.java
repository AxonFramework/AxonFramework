package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.mongo.utils.MongoLauncher;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.bson.Document;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context.xml"})
public class MongoTokenStoreTest {

    private TokenStore tokenStore;
    private TokenStore tokenStoreDifferentOwner;

    private static MongodExecutable mongoExe;
    private static MongodProcess mongod;

    private MongoTemplate mongoTemplate;
    private MongoCollection<Document> trackingTokensCollection;
    private Serializer serializer;
    private TemporalAmount claimTimeout = Duration.ofSeconds(5);
    private Class<byte[]> contentType = byte[].class;

    private final String testProcessorName = "testProcessorName";
    private final int testSegment = 10;
    private final String testOwner = "testOwner";

    @Autowired
    private ApplicationContext context;

    @BeforeClass
    public static void startMongoDB() throws Exception {
        mongoExe = MongoLauncher.prepareExecutable();
        mongod = mongoExe.start();
    }

    @AfterClass
    public static void stopMongoDB() throws Exception {
        if (mongod != null) {
            mongod.stop();
        }
        if (mongoExe != null) {
            mongoExe.stop();
        }
    }

    @Before
    public void setUp() throws Exception {
        MongoClient mongoClient = context.getBean(MongoClient.class);
        serializer = new XStreamSerializer();

        mongoTemplate = new DefaultMongoTemplate(mongoClient);
        trackingTokensCollection = mongoTemplate.trackingTokensCollection();
        tokenStore = new MongoTokenStore(mongoTemplate,
                                         serializer,
                                         claimTimeout,
                                         testOwner,
                                         contentType);
        tokenStoreDifferentOwner = new MongoTokenStore(mongoTemplate,
                                                       serializer,
                                                       claimTimeout,
                                                       "anotherOwner",
                                                       contentType);
    }

    @After
    public void tearDown() throws Exception {
        trackingTokensCollection.drop();
    }

    @Test
    public void testClaimAndUpdateToken() throws Exception {
        Assert.assertNull(tokenStore.fetchToken(testProcessorName, testSegment));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, testProcessorName, testSegment);
        Assert.assertEquals(token, tokenStore.fetchToken(testProcessorName, testSegment));
    }

    @Test(expected = UnableToClaimTokenException.class)
    public void testAttemptToClaimAlreadyClaimedToken() throws Exception {
        Assert.assertNull(tokenStore.fetchToken(testProcessorName, testSegment));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, testProcessorName, testSegment);
        tokenStoreDifferentOwner.storeToken(token, testProcessorName, testSegment);
    }

    @Test
    public void testReleaseClaimAndExtendClaim() throws Exception {
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, testProcessorName, testSegment);

        try {
            tokenStoreDifferentOwner.fetchToken(testProcessorName, testSegment);
            Assert.fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException exception) {
            // expected
        }

        tokenStore.releaseClaim(testProcessorName, testSegment);
        tokenStoreDifferentOwner.extendClaim(testProcessorName, testSegment);

        Assert.assertEquals(token, tokenStoreDifferentOwner.fetchToken(testProcessorName, testSegment));
    }

    @Test
    public void testConcurrentAccess() throws Exception {
        final int attempts = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(attempts);

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            final int iteration = i;
            Future<Integer> future = executorService.submit(() -> {
                try {
                    String owner = String.valueOf(iteration);
                    TokenStore tokenStore = new MongoTokenStore(mongoTemplate,
                                                                serializer,
                                                                claimTimeout,
                                                                owner,
                                                                contentType);
                    GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(iteration);
                    tokenStore.storeToken(token, testProcessorName, testSegment);
                    return iteration;
                } catch (UnableToClaimTokenException exception) {
                    return null;
                }
            });
            futures.add(future);
        }
        executorService.shutdown();
        Assert.assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));

        List<Future<Integer>> successfulAttempts = futures.stream()
                                                          .filter(future -> {
                                                              try {
                                                                  return future.get() != null;
                                                              } catch (InterruptedException e) {
                                                                  return false;
                                                              } catch (ExecutionException e) {
                                                                  return false;
                                                              }
                                                          })
                                                          .collect(Collectors.toList());
        Assert.assertEquals(1, successfulAttempts.size());

        Integer iterationOfSuccessfulAttempt = successfulAttempts.get(0)
                                                                 .get();
        TokenStore tokenStore = new MongoTokenStore(mongoTemplate,
                                                    serializer,
                                                    claimTimeout,
                                                    String.valueOf(iterationOfSuccessfulAttempt),
                                                    contentType);

        Assert.assertEquals(new GlobalSequenceTrackingToken(iterationOfSuccessfulAttempt),
                            tokenStore.fetchToken(testProcessorName, testSegment));
    }
}