package org.axonframework.contextsupport.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.axonframework.commandhandling.CommandBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:contexts/axon-namespace-support-context.xml"})
public class SimpleCommandBusBeanDefinitionParserTest {

	@Autowired
	private DefaultListableBeanFactory beanFactory;
	
	@Test
	public void embeddedRefInterceptorDefinitionTest() {
		BeanDefinition commandBusEmbeddedRef = beanFactory.getBeanDefinition("commandBus-embedded-ref");
		assertNotNull("Bean definition not created correctly", commandBusEmbeddedRef);
		PropertyValue propertyValue = commandBusEmbeddedRef.getPropertyValues().getPropertyValue("interceptors");
		assertNotNull("No definition for the interceptor", propertyValue);
		ManagedList<?> list = (ManagedList<?>) propertyValue.getValue();
		assertTrue(RuntimeBeanReference.class.isInstance(list.get(0)));
		RuntimeBeanReference beanReference = (RuntimeBeanReference) list.get(0);
		assertEquals("commandbus-interceptor", beanReference.getBeanName());
		
		CommandBus commandBus = beanFactory.getBean("commandBus-embedded-ref", CommandBus.class);
		assertNotNull(commandBus);
	}

	@Test
	public void interceptorAttrinbuteDefinitionTest() {
		BeanDefinition commandBusAttribute = beanFactory.getBeanDefinition("commandBus-interceptor-attribute");
		assertNotNull("Bean definition not created correctly", commandBusAttribute);
		PropertyValue propertyValue = commandBusAttribute.getPropertyValues().getPropertyValue("interceptors");
		assertNotNull("No definition for the interceptor", propertyValue);
		assertTrue(RuntimeBeanReference.class.isInstance(propertyValue.getValue()));
		RuntimeBeanReference beanReference = (RuntimeBeanReference) propertyValue.getValue();
		assertEquals("commandbus-interceptor", beanReference.getBeanName());
		
		CommandBus commandBus = beanFactory.getBean("commandBus-interceptor-attribute", CommandBus.class);
		assertNotNull(commandBus);
	}

	@Test
	public void embeddedInterceptorBeanInterceptorDefinitionTest() {
		BeanDefinition commandBusEmbeddedBean = beanFactory.getBeanDefinition("commandBus-embedded-interceptor-bean");
		assertNotNull("Bean definition not created correctly", commandBusEmbeddedBean);
		PropertyValue propertyValue = commandBusEmbeddedBean.getPropertyValues().getPropertyValue("interceptors");
		assertNotNull("No definition for the interceptor", propertyValue);
		ManagedList<?> list = (ManagedList<?>) propertyValue.getValue();
		assertTrue(BeanDefinitionHolder.class.isInstance(list.get(0)));
		
		CommandBus commandBus = beanFactory.getBean("commandBus-embedded-interceptor-bean", CommandBus.class);
		assertNotNull(commandBus);
	}
}
