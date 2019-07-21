package org.axonframework.queryhandling.config;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.*;
import org.axonframework.queryhandling.updatestore.DistributedQueryUpdateStore;
import org.axonframework.queryhandling.updatestore.JmxSubscriberIdentityService;
import org.axonframework.queryhandling.updatestore.SubscriberIdentityService;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@RegisterDefaultEntities(packages = {
        "org.axonframework.queryhandling.updatestore.model"
})
/*
 * mutually exclusive
 */
@EnableAutoConfiguration(exclude = AxonServerAutoConfiguration.class)
public class DistributedQueryBusAutoConfiguration {

    @Primary
    @Bean("queryUpdateEmitter")
    @DependsOn("localSegment")
    public DistributedQueryUpdateEmitter distributedQueryUpdateEmitter() {
        return new DistributedQueryUpdateEmitter();
    }

    @Primary
    @Bean("queryBus")
    @DependsOn("localSegment")
    public DistributedQueryBus distributedQueryBus() {
        return new DistributedQueryBus();
    }

    @ConditionalOnMissingBean(QueryUpdatePollingService.class)
    @Bean
    public QueryUpdatePollingService queryUpdatePollingService() {
        return new QueryUpdatePollingSingleThreadService();
    }

    @ConditionalOnMissingBean(QueryUpdateStore.class)
    @Bean
    public QueryUpdateStore queryUpdateStore() {
        return new DistributedQueryUpdateStore();
    }

    @ConditionalOnMissingBean(SubscriberIdentityService.class)
    @Bean
    public SubscriberIdentityService subscriberIdentityService() {
        return new JmxSubscriberIdentityService();
    }

    /*
     * copy from org.axonframework.config.DefaultConfigurer.defaultQueryUpdateEmitter
     */
    @ConditionalOnMissingQualifiedBean(qualifier = "localQueryUpdateEmitter")
    @Bean("localQueryUpdateEmitter")
    public QueryUpdateEmitter localQueryUpdateEmitter(AxonConfiguration config) {
        MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor =
                config.messageMonitor(QueryUpdateEmitter.class, "queryUpdateEmitter");
        return SimpleQueryUpdateEmitter.builder()
                .updateMessageMonitor(updateMessageMonitor)
                .build();
    }

    // FROM AxonAutoConfiguration START

    // @ConditionalOnMissingBean(QueryInvocationErrorHandler.class)
    @Bean("localSegment")
    public SimpleQueryBus queryBus(AxonConfiguration axonConfiguration,
                                   TransactionManager transactionManager,
                                   @Qualifier("localQueryUpdateEmitter") QueryUpdateEmitter localQueryUpdateEmitter) {
        return SimpleQueryBus.builder()
                .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                .transactionManager(transactionManager)
                .errorHandler(axonConfiguration.getComponent(
                        QueryInvocationErrorHandler.class,
                        () -> LoggingQueryInvocationErrorHandler.builder().build()
                ))
                .queryUpdateEmitter(localQueryUpdateEmitter)
                .build();
    }

    // FROM AxonAutoConfiguration END

}
