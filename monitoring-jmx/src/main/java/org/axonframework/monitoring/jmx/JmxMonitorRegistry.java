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

package org.axonframework.monitoring.jmx;

import org.axonframework.monitoring.MonitorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Utility class that allows Axon-related components to be configured with an MBeanServer. By default all MBeans are
 * registered with the platform MBean Server using a standard ObjectName.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 0.6
 */
public final class JmxMonitorRegistry extends MonitorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(JmxMonitorRegistry.class);

    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

    /**
     * Attempts to register the given <code>mBean</code> as an MBean with the default MBeanServer. If registration
     * fails, no exceptions are thrown. Instead, failure is logged and silently accepted.
     *
     * @param mBean         The instance to register as MBean. Note that this instance needs to be MBean compliant.
     *                      Otherwise, registration fails silently.
     * @param componentType The type of component that the monitoring bean provides information for
     */
    @Override
    public void registerBean(Object mBean, Class<?> componentType) {
        try {
            mBeanServer.registerMBean(new StandardMBean(mBean, null, true), objectNameFor(componentType));
        } catch (InstanceAlreadyExistsException e) {
            logger.warn("Object {} has already been registered as an MBean", mBean);
        } catch (MBeanRegistrationException e) {
            logger.error("An error occurred registering an MBean", e);
        } catch (NotCompliantMBeanException e) {
            logger.error("Non-compliant MBean registered.", e);
        }
    }

    private ObjectName objectNameFor(Class<?> clazz) {
        try {
            ObjectName objectName = new ObjectName("org.axonframework", "type", clazz.getSimpleName());
            int i = 1;
            while (!mBeanServer.queryMBeans(objectName, null).isEmpty()) {
                objectName = new ObjectName("org.axonframework", "type", clazz.getSimpleName() + "_" + i++);
            }
            return objectName;
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("This JVM doesn't seem to accept perfectly normal ObjectNames");
        }
    }
}
