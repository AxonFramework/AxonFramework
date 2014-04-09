/*
 * Copyright (c) 2010-2014. Axon Framework
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
     * @param componentType  The type of component that the monitoring bean provides information for
     */
    public static void registerMonitoringBean(Object monitoringBean, Class<?> componentType) {
        for (MonitorRegistry registry : registries) {
            try {
                registry.registerBean(monitoringBean, componentType);
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
     * @param componentType  The type of component that the monitoring bean provides information for
     */
    protected abstract void registerBean(Object monitoringBean, Class<?> componentType);
}
