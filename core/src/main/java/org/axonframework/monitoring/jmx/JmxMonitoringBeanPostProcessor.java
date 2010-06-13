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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Allard Buijze
 * @since 0.6
 */
public class JmxMonitoringBeanPostProcessor implements BeanPostProcessor, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(JmxMonitoringBeanPostProcessor.class);

    private List<Monitor> statistics = new ArrayList<Monitor>();
    private MBeanServer mBeanServer;
    private boolean monitorsEnabled;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Monitor) {
            try {
                mBeanServer.registerMBean(bean, new ObjectName("AxonFramework", "name", beanName));
                if (monitorsEnabled) {
                    ((Monitor) bean).enable();
                }
            } catch (InstanceAlreadyExistsException e) {
                logger.warn("Could not register a discovered MBean with the MBean Server", e);
            } catch (MBeanRegistrationException e) {
                logger.warn("Could not register a discovered MBean with the MBean Server", e);
            } catch (NotCompliantMBeanException e) {
                logger.info("Bean with name {} does not seem a valid MBean. Registration skipped.", beanName);
            } catch (MalformedObjectNameException e) {
                logger.warn("Could not register a discovered MBean with the MBean Server", e);
            }
        }
        return bean;
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

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.mBeanServer == null) {
            this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
        }
    }
}
