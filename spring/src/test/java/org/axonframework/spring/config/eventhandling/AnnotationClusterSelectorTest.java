/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.spring.config.StubDomainEvent;
import org.axonframework.spring.config.eventhandling.AnnotationClusterSelector;
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
        cluster = new SimpleCluster("cluster");
        testSubject = new AnnotationClusterSelector(MyInheritedAnnotation.class, cluster);
    }

    @Test
    public void testSelectClusterForAnnotatedHandler() {
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new AnnotatedEventHandler()));
        assertSame(cluster, actual);
    }

    @Test
    public void testSelectClusterForAnnotatedHandlerSubClass() {
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertSame(cluster, actual);
    }

    @Test
    public void testReturnNullWhenNoAnnotationFound() {
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new NonAnnotatedEventHandler()));
        assertNull("ClusterSelector should not have selected a cluster", actual);
    }

    @Test
    public void testSelectClusterForNonInheritedHandlerSubClassWhenSuperClassInspectionIsEnabled() {
        testSubject = new AnnotationClusterSelector(MyAnnotation.class, cluster, true);
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertSame(cluster, actual);
    }

    @Test
    public void testReturnNullForNonInheritedHandlerSubClassWhenSuperClassInspectionIsDisabled() {
        testSubject = new AnnotationClusterSelector(MyAnnotation.class, cluster);
        Cluster actual = testSubject.selectCluster(new AnnotationEventListenerAdapter(new AnnotatedSubEventHandler()));
        assertNull("ClusterSelector should not have selected a cluster", actual);
    }

    @MyInheritedAnnotation
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
    public static @interface MyInheritedAnnotation {

    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface MyAnnotation {

    }


}
