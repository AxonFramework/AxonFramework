package org.axonframework.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Abstract Factory providing access to the MonitorRegistry implementations available at runtime. The MonitorRegistry
 * allows beans providing Monitoring and Management services to be registered with the MonitorRegistry implementations
 * on the classpath.
 * <p/>
 * If no MonitorRegistry implementations are available on the classpath, nothing happens.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class MonitorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MonitorRegistry.class);
    private static List<MonitorRegistry> registries;

    static {
        registries = new ArrayList<MonitorRegistry>();
        ServiceLoader<MonitorRegistry> registryLoader = ServiceLoader.load(MonitorRegistry.class);
        for (MonitorRegistry monitorRegistry : registryLoader) {
            registries.add(monitorRegistry);
        }
    }

    /**
     * Register the given <code>monitoringBean</code> with the registries on the classpath. If an exception or error
     * occurs while registering the monitoringBean, it is ignored.
     *
     * @param monitoringBean The bean containing the monitoring information
     */
    public static void registerMonitoringBean(Object monitoringBean) {
        for (MonitorRegistry registry : registries) {
            try {
                registry.registerBean(monitoringBean);
            } catch (Exception e) {
                logger.warn("Exception when registering {} with {} ", new Object[]{monitoringBean, registry, e});
            } catch (Error e) {
                logger.warn("Error when registering {} with {} ", new Object[]{monitoringBean, registry, e});
            }
        }
    }

    /**
     * Registers the bean with the Registry. This bean may be an infrastructure component, or an Object that provides
     * information and management services on its behalf.
     *
     * @param monitoringBean The bean to register
     */
    protected abstract void registerBean(Object monitoringBean);
}
