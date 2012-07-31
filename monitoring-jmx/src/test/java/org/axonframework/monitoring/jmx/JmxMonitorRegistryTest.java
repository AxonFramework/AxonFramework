/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.eventhandling.SimpleEventBus;
import org.junit.*;

import java.lang.management.ManagementFactory;
import java.util.Set;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class JmxMonitorRegistryTest {

    @Test
    public void testRegisterBeanTwice()
            throws MalformedObjectNameException, InterruptedException, MBeanRegistrationException,
                   InstanceAlreadyExistsException, NotCompliantMBeanException {
        SimpleEventBus sb = new SimpleEventBus();
        SimpleEventBus sb2 = new SimpleEventBus();

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> mbeans = mBeanServer.queryNames(new ObjectName("org.axonframework", "type", "Simple*"), null);
        assertTrue(mbeans.contains(new ObjectName("org.axonframework", "type", "SimpleEventBus")));
        assertTrue(mbeans.contains(new ObjectName("org.axonframework", "type", "SimpleEventBus_1")));
        assertEquals(2, mbeans.size());
    }

    @MXBean
    public static class Some implements SomeMBean {

    }

    public static interface SomeMBean {

    }
}
