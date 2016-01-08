/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.spring.config.xml;

import org.junit.*;
import org.mockito.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AutowiredDependencyFactoryBeanTest {

    private ApplicationContext mockApplicationContext;

    private Map<Class, List<String>> beansOfType = new HashMap<>();
    private Map<String, Boolean> primaryStatusPerBean = new HashMap<>();

    private AutowiredDependencyFactoryBean testSubject;

    @Before
    public void setUp() throws Exception {
        mockApplicationContext = mock(ApplicationContext.class);
        ConfigurableListableBeanFactory mockBeanFactory = mock(ConfigurableListableBeanFactory.class);

        when(mockApplicationContext.getAutowireCapableBeanFactory()).thenReturn(mockBeanFactory);
        when(mockApplicationContext.getBean(anyString())).thenReturn("mockBean");
        when(mockBeanFactory.containsBeanDefinition(anyString())).thenReturn(true);
        when(mockApplicationContext.getBeanNamesForType(Matchers.<Class<?>>any())).thenAnswer(invocation -> {
            List<String> beanNames = beansOfType.get(invocation.getArgumentAt(0, Class.class));
            if (beanNames == null) {
                beanNames = new ArrayList<>();
            }
            return beanNames.toArray(new String[beanNames.size()]);
        });
        when(mockBeanFactory.getBeanDefinition(anyString())).thenAnswer(invocation -> {
            assertTrue(primaryStatusPerBean.containsKey(invocation.getArgumentAt(0, String.class)));
            boolean isPrimary = primaryStatusPerBean.get(invocation.getArgumentAt(0, String.class));
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            when(beanDefinition.isPrimary()).thenReturn(isPrimary);
            return beanDefinition;
        });
    }

    @Test
    public void testAutowireSingleCandidate() throws Exception {
        registerBean("theOne", Object.class, false);

        testSubject = new AutowiredDependencyFactoryBean(Object.class);
        testSubject.setApplicationContext(mockApplicationContext);
        testSubject.afterPropertiesSet();

        assertNotNull(testSubject.getObject());

        verify(mockApplicationContext).getBean("theOne");
    }

    @Test
    public void testAutowireSecundaryType() throws Exception {
        registerBean("theSecondOne", String.class, false);

        testSubject = new AutowiredDependencyFactoryBean(Object.class, String.class);
        testSubject.setApplicationContext(mockApplicationContext);
        testSubject.afterPropertiesSet();

        assertNotNull(testSubject.getObject());

        verify(mockApplicationContext).getBean("theSecondOne");
        verify(mockApplicationContext).getBeanNamesForType(Object.class);
        verify(mockApplicationContext).getBeanNamesForType(String.class);
    }

    @Test
    public void testAutowireFallback() throws Exception {
        final Object defaultBean = new Object();
        testSubject = new AutowiredDependencyFactoryBean(defaultBean, Object.class, String.class);
        testSubject.setApplicationContext(mockApplicationContext);
        testSubject.afterPropertiesSet();

        assertSame(defaultBean, testSubject.getObject());

        verify(mockApplicationContext, never()).getBean(anyString());
        verify(mockApplicationContext).getBeanNamesForType(Object.class);
        verify(mockApplicationContext).getBeanNamesForType(String.class);
    }

    @Test
    public void testAutowirePrimaryWhenMultipleCandidates() throws Exception {
        registerBean("theOne", String.class, true);
        registerBean("theSecondOne", String.class, false);

        testSubject = new AutowiredDependencyFactoryBean(Object.class, String.class);
        testSubject.setApplicationContext(mockApplicationContext);
        testSubject.afterPropertiesSet();

        assertNotNull(testSubject.getObject());

        verify(mockApplicationContext).getBean("theOne");
        verify(mockApplicationContext).getBeanNamesForType(Object.class);
        verify(mockApplicationContext).getBeanNamesForType(String.class);
    }

    @Test
    public void testThrowExceptionWhenNoCandidatesAndNoFallback() throws Exception {
        testSubject = new AutowiredDependencyFactoryBean(Object.class, String.class);
        testSubject.setApplicationContext(mockApplicationContext);
        try {
            testSubject.afterPropertiesSet();
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertTrue("Unexpected message: " + e.getMessage(), e.getMessage().contains("No autowire candidates"));
        }

        verify(mockApplicationContext).getBeanNamesForType(Object.class);
        verify(mockApplicationContext).getBeanNamesForType(String.class);
    }

    @Test
    public void testThrowExceptionWhenMultipleCandidates() throws Exception {
        registerBean("theOne", String.class, true);
        registerBean("theSecondOne", String.class, true);

        testSubject = new AutowiredDependencyFactoryBean(Object.class, String.class);
        testSubject.setApplicationContext(mockApplicationContext);
        try {
            testSubject.afterPropertiesSet();
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("theOne"));
            assertTrue(e.getMessage().contains("theSecondOne"));
        }

        verify(mockApplicationContext).getBeanNamesForType(Object.class);
        verify(mockApplicationContext).getBeanNamesForType(String.class);
    }

    private void registerBean(String beanName, Class<?> objectClass, boolean primary) {
        if (!beansOfType.containsKey(objectClass)) {
            beansOfType.put(objectClass, new ArrayList<>());
        }
        beansOfType.get(objectClass).add(beanName);
        primaryStatusPerBean.put(beanName, primary);
    }
}
