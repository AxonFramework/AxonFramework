package org.axonframework.springboot.autoconfig;

import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CommandLoadFactorProvider} is correctly auto configured.
 *
 * @author Sara Pellegrini
 */
@ContextConfiguration
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:application.commandLoadFactor-autoConfiguration.properties")
public class AxonServerAutoConfigurationLoadFactorTest {

    @Autowired
    private CommandLoadFactorProvider commandLoadFactorProvider;

    @Test
    public void loadFactor() {
        int loadFactor = commandLoadFactorProvider.getFor("anything");
        assertEquals(36, loadFactor);
    }
}
