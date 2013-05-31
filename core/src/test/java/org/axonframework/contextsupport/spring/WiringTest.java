package org.axonframework.contextsupport.spring;

import org.axonframework.repository.Repository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static junit.framework.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class WiringTest {

    @Autowired
    private Repository<RepositoryBeanDefinitionParserTest.EventSourcedAggregateRootMock> testRepository;

    @Test
    public void test() {
        assertNotNull(testRepository);
    }

}
