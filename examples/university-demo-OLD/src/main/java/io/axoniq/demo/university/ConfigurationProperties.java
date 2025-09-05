package io.axoniq.demo.university;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigurationProperties {
    private static final Logger logger = Logger.getLogger(ConfigurationProperties.class.getName());

    boolean axonServerEnabled = true;

    public static ConfigurationProperties defaults() {
        return new ConfigurationProperties();
    }

    public static ConfigurationProperties load() {
        ConfigurationProperties props = new ConfigurationProperties();

        Properties properties = loadPropertiesFile("application.properties");

        if (properties != null) {
            String axonServerEnabled = properties.getProperty("axon.server.enabled");
            if (axonServerEnabled != null) {
                props.axonServerEnabled = Boolean.parseBoolean(axonServerEnabled);
            }
        } else {
            logger.info("No properties file found, using default configuration");
        }

        return props;
    }

    private static Properties loadPropertiesFile(String filename) {
        Properties properties = new Properties();
        try (InputStream input = ConfigurationProperties.class.getClassLoader().getResourceAsStream(filename)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded configuration from " + filename);
                return properties;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading properties file " + filename + ": " + e.getMessage());
        }
        return null;
    }

    public boolean axonServerEnabled() {
        return axonServerEnabled;
    }

    public ConfigurationProperties axonServerEnabled(boolean axonServerEnabled) {
        this.axonServerEnabled = axonServerEnabled;
        return this;
    }
}
