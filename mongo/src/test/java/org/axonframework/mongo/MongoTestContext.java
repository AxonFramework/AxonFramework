package org.axonframework.mongo;

import com.mongodb.MongoClient;
import org.axonframework.mongo.eventhandling.saga.repository.MongoSagaStore;
import org.axonframework.mongo.eventsourcing.eventstore.MongoEventStorageEngine;
import org.axonframework.mongo.eventsourcing.eventstore.MongoFactory;
import org.axonframework.mongo.eventsourcing.eventstore.MongoOptionsFactory;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.Mockito.*;

@Configuration
public class MongoTestContext {

    @Bean
    public MongoEventStorageEngine eventStore(MongoTemplate template) {
        return new MongoEventStorageEngine(template);
    }

    @Bean
    public MongoTemplate template(MongoClient mongoDb) {
        return new DefaultMongoTemplate(mongoDb);
    }

    @Bean
    public MongoClient mongoDb(MongoFactory mongoFactory) {
        return mongoFactory.createMongo();
    }

    @Bean
    public MongoFactory mongoFactoryBean(MongoOptionsFactory mongoOptionsFactory) {
        MongoFactory mongoFactory = new MongoFactory();
        mongoFactory.setMongoOptions(mongoOptionsFactory.createMongoOptions());
        return mongoFactory;
    }

    @Bean
    public MongoOptionsFactory mongoOptionsFactory() {
        MongoOptionsFactory mongoOptionsFactory = new MongoOptionsFactory();
        mongoOptionsFactory.setConnectionsPerHost(100);
        return mongoOptionsFactory;
    }

    @Bean
    public SpringResourceInjector springResourceInjector() {
        return new SpringResourceInjector();
    }

    @Bean
    public MongoSagaStore mongoSagaStore(MongoTemplate sagaMongoTemplate) {
        return MongoSagaStore.builder()
                             .mongoTemplate(sagaMongoTemplate)
                             .build();
    }

    @Bean
    public MongoTemplate sagaMongoTemplate(MongoClient mongoDb) {
        return new DefaultMongoTemplate(mongoDb);
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        return mock(PlatformTransactionManager.class);
    }
}
