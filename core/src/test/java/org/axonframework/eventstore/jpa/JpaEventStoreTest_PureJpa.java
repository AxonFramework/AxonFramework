package org.axonframework.eventstore.jpa;

import org.junit.runner.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/META-INF/spring/db-context.xml",
        "classpath:/META-INF/spring/eventstore-jpa-test-context.xml"})
public class JpaEventStoreTest_PureJpa extends JpaEventStoreTestCases {

}
