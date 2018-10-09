/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.mongo.DefaultMongoTemplate;
import org.axonframework.mongo.MongoTemplate;
import org.axonframework.mongo.MongoTestContext;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MongoTestContext.class)
public class MongoTokenStoreTest {

    private MongoTokenStore tokenStore;
    private MongoTokenStore tokenStoreDifferentOwner;

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
    public static void stopMongoDB() {
        if (mongod != null) {
            mongod.stop();
        }
        if (mongoExe != null) {
            mongoExe.stop();
        }
    }

    @Before
    public void setUp() {
        MongoClient mongoClient = context.getBean(MongoClient.class);
        serializer = XStreamSerializer.builder().build();

        mongoTemplate = DefaultMongoTemplate.builder().mongoDatabase(mongoClient).build();
        trackingTokensCollection = mongoTemplate.trackingTokensCollection();
        trackingTokensCollection.drop();

        MongoTokenStore.Builder tokenStoreBuilder = MongoTokenStore.builder()
                                                                   .mongoTemplate(mongoTemplate)
                                                                   .serializer(serializer)
                                                                   .claimTimeout(claimTimeout)
                                                                   .contentType(contentType);
        tokenStore = tokenStoreBuilder.nodeId(testOwner).build();
        tokenStore.ensureIndexes();
        tokenStoreDifferentOwner = tokenStoreBuilder.nodeId("anotherOwner").build();
    }

    @After
    public void tearDown() {
        trackingTokensCollection.drop();
    }

    @Test
    public void testClaimAndUpdateToken() {
        assertNull(tokenStore.fetchToken(testProcessorName, testSegment));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, testProcessorName, testSegment);
        assertEquals(token, tokenStore.fetchToken(testProcessorName, testSegment));
    }

    @Test
    public void testInitializeTokens() {
        tokenStore.initializeTokenSegments("test1", 7);

        int[] actual = tokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testInitializeTokensAtGivenPosition() {
        tokenStore.initializeTokenSegments("test1", 7, new GlobalSequenceTrackingToken(10));

        int[] actual = tokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, actual);

        for (int segment : actual) {
            assertEquals(new GlobalSequenceTrackingToken(10), tokenStore.fetchToken("test1", segment));
        }
    }

    @Test(expected = UnableToClaimTokenException.class)
    public void testInitializeTokensWhileAlreadyPresent() {
        tokenStore.fetchToken("test1", 1);
        tokenStore.initializeTokenSegments("test1", 7);
    }

    @Test(expected = UnableToClaimTokenException.class)
    public void testAttemptToClaimAlreadyClaimedToken() {
        assertNull(tokenStore.fetchToken(testProcessorName, testSegment));
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, testProcessorName, testSegment);
        tokenStoreDifferentOwner.storeToken(token, testProcessorName, testSegment);
    }

    @Test(expected = UnableToClaimTokenException.class)
    public void testAttemptToExtendClaimOnAlreadyClaimedToken() {
        assertNull(tokenStore.fetchToken(testProcessorName, testSegment));
        tokenStoreDifferentOwner.extendClaim(testProcessorName, testSegment);
    }

    @Test
    public void testClaimAndExtend() {
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, testProcessorName, testSegment);

        try {
            tokenStoreDifferentOwner.fetchToken(testProcessorName, testSegment);
            Assert.fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException exception) {
            // expected
        }

        tokenStore.extendClaim(testProcessorName, testSegment);
    }

    @Test(expected = UnableToClaimTokenException.class)
    public void testReleaseClaimAndExtendClaim() {
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
    }

    @Test
    public void testFetchSegments() {
        tokenStore.fetchToken("processor1", 1);
        tokenStore.fetchToken("processor1", 0);
        tokenStore.fetchToken("processor1", 2);
        tokenStore.fetchToken("processor2", 0);

        assertArrayEquals(new int[]{0, 1, 2}, tokenStore.fetchSegments("processor1"));
        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments("processor2"));
        assertArrayEquals(new int[0], tokenStore.fetchSegments("processor3"));
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
                    TokenStore tokenStore = MongoTokenStore.builder()
                                                           .mongoTemplate(mongoTemplate)
                                                           .serializer(serializer)
                                                           .claimTimeout(claimTimeout)
                                                           .nodeId(owner)
                                                           .contentType(contentType)
                                                           .build();
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
                                                              } catch (InterruptedException | ExecutionException e) {
                                                                  return false;
                                                              }
                                                          })
                                                          .collect(Collectors.toList());
        assertEquals(1, successfulAttempts.size());

        Integer iterationOfSuccessfulAttempt = successfulAttempts.get(0)
                                                                 .get();
        TokenStore tokenStore = MongoTokenStore.builder()
                                               .mongoTemplate(mongoTemplate)
                                               .serializer(serializer)
                                               .claimTimeout(claimTimeout)
                                               .nodeId(String.valueOf(iterationOfSuccessfulAttempt))
                                               .contentType(contentType)
                                               .build();

        assertEquals(new GlobalSequenceTrackingToken(iterationOfSuccessfulAttempt),
                     tokenStore.fetchToken(testProcessorName, testSegment));
    }
}
