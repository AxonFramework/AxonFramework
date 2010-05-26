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

package org.axonframework.monitoring;

import javax.annotation.PostConstruct;
import javax.management.*;
import java.lang.management.ManagementFactory;

/**
 * <p>Registers the provided beans with the platform MBean server.</p>
 * <p>You can enable all statistics by setting the enabled property to true.</p>
 *
 * @author Jettro Coenradie
 * @since 0.6
 */
public class ManagementContext {
    private MBeanServer mbeanServer;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        mbeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    public void registerMBean(Statistics mxBean, String objectName) {
        try {
            if (enabled) {
                mxBean.enable();
            }
            ObjectName eventBusName = new ObjectName("BaseJmxAgent:name=" + objectName);
            mbeanServer.registerMBean(mxBean, eventBusName);
        } catch (MalformedObjectNameException e) {
            throw new ManagementBeanRegistrationException(e);
        } catch (NotCompliantMBeanException e) {
            throw new ManagementBeanRegistrationException(e);
        } catch (InstanceAlreadyExistsException e) {
            throw new ManagementBeanRegistrationException(e);
        } catch (MBeanRegistrationException e) {
            throw new ManagementBeanRegistrationException(e);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
