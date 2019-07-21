package org.axonframework.queryhandling.repository;

import org.axonframework.queryhandling.config.DistributedQueryBusJpaAutoConfiguration;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        JacksonSerializerTestConfig.class,
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DistributedQueryBusJpaAutoConfiguration.class
})
@ActiveProfiles({"spring-test-hsqldb"})
public class RepositoriesJpaTest extends AbstractRepositoriesTest {

}
