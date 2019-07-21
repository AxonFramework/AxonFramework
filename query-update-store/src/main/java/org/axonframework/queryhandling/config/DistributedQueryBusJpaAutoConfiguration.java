package org.axonframework.queryhandling.config;

import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@ConditionalOnProperty(
        prefix = "axon.queryhandling",
        name = "updatestore.backend",
        havingValue = "jpa",
        matchIfMissing = true
)
@Configuration
@RegisterDefaultEntities(packages = "org.axonframework.queryhandling.updatestore.model")
@EnableJpaRepositories(basePackages = "org.axonframework.queryhandling.updatestore.repository")
public class DistributedQueryBusJpaAutoConfiguration {

}
