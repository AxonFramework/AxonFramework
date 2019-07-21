package org.axonframework.queryhandling.updatestore.repository;

import org.axonframework.queryhandling.SubscriptionId;
import org.axonframework.queryhandling.updatestore.model.QueryUpdateEntity;
import org.axonframework.queryhandling.updatestore.repository.redis.SubscriptionIdRedisStringWriter;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryUpdateRepository extends CrudRepository<QueryUpdateEntity, Long> {

    default List<QueryUpdateEntity> findBySubscriptionId(SubscriptionId subscriptionId) {
        return this.findBySubscriptionId(new SubscriptionIdRedisStringWriter().convert(subscriptionId));
    }

    List<QueryUpdateEntity> findBySubscriptionId(String subscriptionId);

}
