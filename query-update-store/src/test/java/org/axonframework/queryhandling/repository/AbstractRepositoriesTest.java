package org.axonframework.queryhandling.repository;

import demo.DemoQueryResult;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionId;
import org.axonframework.queryhandling.updatestore.model.QueryUpdateEntity;
import org.axonframework.queryhandling.updatestore.model.SubscriptionEntity;
import org.axonframework.queryhandling.updatestore.repository.QueryUpdateRepository;
import org.axonframework.queryhandling.updatestore.repository.SubscriptionRepository;
import org.axonframework.serialization.Serializer;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

import static org.axonframework.queryhandling.repository.RepositoryTestUtil.javaPersistenceTimeDiscrepancy;
import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public abstract class AbstractRepositoriesTest {
    @Resource
    private SubscriptionRepository subscriptionRepository;

    @Resource
    private QueryUpdateRepository queryUpdateRepository;

    @Resource
    private Serializer messageSerializer;

    @Test
    public void testSubscriptionCreate() {
        // arrange
        SubscriptionId id =
                new SubscriptionId(
                        "mockedNodeId",
                        "9d4568c009d203ab10e33ea9953a0264");

        // act
        SubscriptionEntity subscription = subscriptionRepository.createSubscription(
                id,
                new byte[0],
                ResponseTypes.instanceOf(DemoQueryResult.class),
                ResponseTypes.instanceOf(DemoQueryResult.class),
                messageSerializer);

        Optional<SubscriptionEntity> subscription2Opt = subscriptionRepository.findById(id);

        // assert
        assertEquals(id, subscription.getId());

        assertTrue(subscription2Opt.isPresent());
        SubscriptionEntity subscription2 = subscription2Opt.get();

        assertEquals(subscription.getId(), subscription2.getId());
        assertArrayEquals(
                subscription.getQueryPayload(),
                subscription2.getQueryPayload()
        );

        assertTrue(javaPersistenceTimeDiscrepancy(
                subscription.getCreationTime(),
                subscription2.getCreationTime(),
                1000L
        ));
    }


    @Test
    public void testSubscriptionDelete() {
        // arrange
        SubscriptionId id =
                new SubscriptionId(
                        "mockedNodeId",
                        "9d4568c009d203ab10e33ea9953a0264");

        SubscriptionEntity subscription = subscriptionRepository.createSubscription(
                id,
                new byte[0],
                ResponseTypes.instanceOf(DemoQueryResult.class),
                ResponseTypes.instanceOf(DemoQueryResult.class),
                messageSerializer);

        // act

        assertTrue(subscriptionRepository.findById(id).isPresent());

        subscriptionRepository.delete(subscription);

        // assert
        assertFalse(subscriptionRepository.findById(id).isPresent());
    }

    @Test
    public void testUpdatePostAndPeek() {
        // arrange
        SubscriptionId subscriptionId =
                new SubscriptionId(
                        "mockedNodeId",
                        "9d4568c009d203ab10e33ea9953a0264");

        subscriptionRepository.createSubscription(
                subscriptionId,
                new byte[0],
                ResponseTypes.instanceOf(DemoQueryResult.class),
                ResponseTypes.instanceOf(DemoQueryResult.class),
                messageSerializer);

        QueryUpdateEntity update = new QueryUpdateEntity(
                subscriptionId,
                new GenericSubscriptionQueryUpdateMessage<>(
                        new DemoQueryResult("mockedAggId")
                ),
                messageSerializer);

        // act
        queryUpdateRepository.save(update);

        List<QueryUpdateEntity> bySubscription =
                queryUpdateRepository.findBySubscriptionId(subscriptionId);

        // assert
        assertFalse(bySubscription.isEmpty());
        QueryUpdateEntity update2 = bySubscription.get(0);
        assertEquals(update.getId(), update2.getId());
        assertArrayEquals(
                update.getUpdatePayload(),
                update2.getUpdatePayload()
        );
        assertTrue(
                javaPersistenceTimeDiscrepancy(
                        update.getCreationTime(),
                        update2.getCreationTime(),
                        1000L
                )
        );
    }
}
