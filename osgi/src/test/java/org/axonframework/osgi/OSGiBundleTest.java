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
package orgaxonframework.osgi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.*;

/**
 * @author lburgazzoli
 */
@RunWith(PaxExam.class)
public class OSGiBundleTest {

    private static final String   AXON_GROUP     = "org.axonframework";
    private static final String[] AXON_ARTIFACTS = new String[] {"axon-core","axon-amqp","axon-distributed-commandbus"};

    @Inject
    BundleContext context;

    @Configuration
    public Option[] config() {
        return options(
            systemProperty("org.osgi.framework.storage.clean").value("true"),
            systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("WARN"),
            repository("http://repository.springsource.com/maven/bundles/release@id=spring.ebr.release"),
            mavenBundle("org.slf4j","slf4j-api",System.getProperty("slf4j.version")),
            mavenBundle("org.slf4j","slf4j-log4j12",System.getProperty("slf4j.version")).noStart(),
            mavenBundle("log4j","log4j",System.getProperty("log4j.version")),
            mavenBundle("com.rabbitmq","amqp-client","2.8.6"),
            new File("core/target/classes").exists()
                ?  bundle("reference:file:core/target/classes")
                :  bundle("reference:file:../core/target/classes"),
            new File("amqp/target/classes").exists()
                ?  bundle("reference:file:amqp/target/classes")
                :  bundle("reference:file:../amqp/target/classes"),
            new File("distributed-commandbus/target/classes").exists()
                ?  bundle("reference:file:distributed-commandbus/target/classes")
                :  bundle("reference:file:../distributed-commandbus/target/classes"),
            junitBundles(),
            systemPackage("com.sun.tools.attach"),
            cleanCaches()
        );
    }

    @Test
    public void checkInject() {
        assertNotNull(context);
    }

    @Test
    public void checkBundles() {
        Map<String,Bundle> axonBundles = new HashMap<String,Bundle>();

        for(Bundle bundle : context.getBundles()) {
            if(bundle != null) {
                if(bundle.getSymbolicName().startsWith(AXON_GROUP)) {
                    axonBundles.put(bundle.getSymbolicName(),bundle);
                }
            }
        }

        for(String artifact : AXON_ARTIFACTS) {
            assertTrue(axonBundles.containsKey(AXON_GROUP + "." + artifact));
            assertTrue(axonBundles.get(AXON_GROUP + "." + artifact).getState() == Bundle.ACTIVE);
        }
    }
}
