package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringConfigurerTest {

    private ConfigurableListableBeanFactory context;

    @BeforeEach
    void setUp() {
        context = mock(ConfigurableListableBeanFactory.class);
        when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[]{});
    }

    @Test
    void springComponentLoadedWhenNoComponentConfigured() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus"});

        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        assertSame(commandBus, configurer.buildConfiguration().commandBus());
    }

    @Test
    void springPrimaryBeanUsedWhenMultipleCandidates() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus", "alternative"});
        when(context.getBeanDefinition(any())).thenReturn(new GenericBeanDefinition());
        GenericBeanDefinition primary = new GenericBeanDefinition();
        primary.setPrimary(true);
        when(context.getBeanDefinition("commandBus")).thenReturn(primary);
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        assertSame(commandBus, configurer.buildConfiguration().commandBus());

        //noinspection unchecked
        verify(context, never()).getBean(eq("alternative"), any(Class.class));
    }

    @Test
    void failsWhenMultiplePrimaryCandidates() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus", "alternative"});
        GenericBeanDefinition primary = new GenericBeanDefinition();
        primary.setPrimary(true);
        when(context.getBeanDefinition(any())).thenReturn(primary);
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        Configuration configuration = configurer.buildConfiguration();
        assertThrows(AxonConfigurationException.class, configuration::commandBus);

        //noinspection unchecked
        verify(context, never()).getBean(anyString(), any(Class.class));
    }

    @Test
    void failsWhenMultipleNonPrimaryCandidates() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus", "alternative"});
        GenericBeanDefinition nonPrimary = new GenericBeanDefinition();
        when(context.getBeanDefinition(any())).thenReturn(nonPrimary);
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        Configuration configuration = configurer.buildConfiguration();
        assertThrows(AxonConfigurationException.class, configuration::commandBus);

        //noinspection unchecked
        verify(context, never()).getBean(anyString(), any(Class.class));
    }

    @Test
    void usesConfigurerDefaultsWhenNoBeansDefined() {
        SpringConfigurer configurer = new SpringConfigurer(context);

        assertNotNull(configurer.buildConfiguration().commandBus());

        //noinspection unchecked
        verify(context, never()).getBean(anyString(), any(Class.class));
    }

}