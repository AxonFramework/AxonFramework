/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.monitoring.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/**
 * Utility class that allows Axon-related components to be configured with an MBeanServer. By default all MBeans are
 * registered with the platform MBean Server using a standard ObjectName.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 0.6
 */
public final class JmxConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JmxConfiguration.class);
    private static final JmxConfiguration INSTANCE = new JmxConfiguration();

    private MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    private boolean enabled;

    private JmxConfiguration() {
    }

    /**
     * Returns the singleton instance of JmxConfiguration.
     *
     * @return the JmxConfiguration instance
     */
    public static JmxConfiguration getInstance() {
        return INSTANCE;
    }

    /**
     * Attempts to register the given <code>mBean</code> as an MBean with the default MBeanServer. If registration
     * fails, no exceptions are thrown. Instead, failure is logged and silently accepted.
     *
     * @param mBean         The instance to register as MBean. Note that this instance needs to be MBean compliant.
     *                      Otherwise, registration fails silently.
     * @param monitoredType The type of object that the MBean represents. This type is used to construct the ObjectName
     *                      of the MBean.
     */
    public void registerMBean(Object mBean, Class<?> monitoredType) {
        if (enabled) {
            try {
                mBeanServer.registerMBean(mBean, objectNameFor(monitoredType));
            } catch (InstanceAlreadyExistsException e) {
                logger.warn("Object {} has already been registered as an MBean", mBean);
            } catch (MBeanRegistrationException e) {
                logger.error("An error occurred registering an MBean", e);
            } catch (NotCompliantMBeanException e) {
                logger.error("Non-compliant MBean registered.", e);
            }
        }
    }

    private ObjectName objectNameFor(Class<?> clazz) {
        try {
            return new ObjectName("org.axonframework", "type", clazz.getSimpleName());
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("This JVM doesn't seem to accept perfectly normal ObjectNames");
        }
    }

    /**
     * Disables monitoring. Any calls to {@link #registerMBean(Object, Class)} will be ignored.
     */
    public void disableMonitoring() {
        this.enabled = false;
    }
}
