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

import org.axonframework.monitoring.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holder class that helps to register jmx monitored beans with the JMX context. The correct order to use this class
 * is to register monitors, set whether to enable detailed monitoring and optionally register an mbean server.
 *
 * After creating the instance of the holder, call the method to register the mbeans with the mbeanserver
 * <code>registerMBeansWithMBeanServer</code>.
 *
 * The platform mbeanserver is used if no mbeanserver is provided.
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public class JmxMonitorHolder {
    private static final Logger logger = LoggerFactory.getLogger(JmxMonitorHolder.class);
    private static Map<String, Monitor> monitors = new ConcurrentHashMap<String, Monitor>();

    private MBeanServer mBeanServer;
    private boolean monitorsEnabled;

    /**
     * Registers the monitor with the holder to be registered with the mbeanserver
     * @param monitorName String representing the name of the monitor to register
     * @param monitor Monitor to register with the MBeanServer
     */
    public static void registerMonitor(String monitorName, Monitor monitor) {
        monitors.put(monitorName, monitor);
    }

    /**
     * Method to be called after construction to register the provided monitors with the mbeanserver
     */
    @PostConstruct
    public void registerMBeansWithMBeanServer() {
        if (this.mBeanServer == null) {
            this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
        }

        for (String key : monitors.keySet()) {
            try {
                mBeanServer.registerMBean(monitors.get(key), new ObjectName("AxonFramework", "name", key));
                if (monitorsEnabled) {
                    monitors.get(key).enable();
                }
            } catch (InstanceAlreadyExistsException e) {
                logger.warn("Could not register a discovered MBean with the MBean Server", e);
            } catch (MBeanRegistrationException e) {
                logger.warn("Could not register a discovered MBean with the MBean Server", e);
            } catch (NotCompliantMBeanException e) {
                logger.info("Bean with name {} does not seem a valid MBean. Registration skipped.", key);
            } catch (MalformedObjectNameException e) {
                logger.warn("Could not register a discovered MBean with the MBean Server", e);
            }

        }
    }

    /**
     * Indicates whether discovered MBeans should be automatically enabled. Defaults to <code>false</code>.
     *
     * @param enabled whether or not all discovered monitors should be enabled by default
     */
    public void setEnableMonitors(boolean enabled) {
        this.monitorsEnabled = enabled;
    }

    /**
     * Sets the MBeanServer to register all detected monitor instances with. Defaults to the platform MBean Server.
     *
     * @param mBeanServer the MBeanServer to register all detected monitor instances with
     */
    public void setMBeanServer(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
    }

}
