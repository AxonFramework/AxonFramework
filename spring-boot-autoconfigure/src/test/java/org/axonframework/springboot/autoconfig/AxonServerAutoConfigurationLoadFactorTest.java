package org.axonframework.springboot.autoconfig;

import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

/**
 * @author Sara Pellegrini
 */
@ContextConfiguration
@EnableAutoConfiguration
@RunWith(SpringRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:application.commandLoadFactor-autoConfiguration.properties")
public class AxonServerAutoConfigurationLoadFactorTest {


    @Autowired
    private CommandLoadFactorProvider commandLoadFactorProvider;

    @Test
    public void testLoadFactor() {
        int loadFactor = commandLoadFactorProvider.getFor("anything");
        assertEquals(36, loadFactor);
    }
}
