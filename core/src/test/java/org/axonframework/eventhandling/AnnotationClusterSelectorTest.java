package org.axonframework.eventhandling;

import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AnnotationClusterSelectorTest {

    private AnnotationClusterSelector testSubject;
    private SimpleCluster cluster;

    @Before
    public void setUp() throws Exception {
        cluster = new SimpleCluster();
        testSubject = new AnnotationClusterSelector(MyAnnotation.class, cluster);
    }

    @Test
    public void testSelectClusterForAnnotatedHandler() {
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new AnnotatedEventHandler(),
                                                                                      null));
        assertSame(cluster, actual);
    }

    @Test
    public void testSelectClusterForAnnotatedHandlerSubClass() {
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler(),
                                                                                      null));
        assertSame(cluster, actual);
    }

    @Test
    public void testReturnNullWhenNoAnnotationFound() {
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new NonAnnotatedEventHandler(),
                                                                                      null));
        assertNull("ClusterSelector should not have selected a cluster", actual);
    }

    @MyAnnotation
    public static class AnnotatedEventHandler {

        @EventHandler
        public void handle(StubDomainEvent event) {
        }
    }

    public static class AnnotatedSubEventHandler extends AnnotatedEventHandler {

    }

    public static class NonAnnotatedEventHandler {

        @EventHandler
        public void handle(StubDomainEvent event) {
        }
    }

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface MyAnnotation {

    }
}
