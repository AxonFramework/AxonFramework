/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;

import java.lang.invoke.MethodHandles;

/**
 * Utility class exposing an operation that searches the {@link ApplicationContext} for auto-configuration base
 * packages.
 *
 * @author Steven van Beelen
 * @since 4.5.4
 */
public abstract class XStreamSecurityTypeUtility {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PACKAGES_AND_SUBPACKAGES_WILDCARD = ".**";

    /**
     * Retrieves the auto-configuration base packages from {@link AutoConfigurationPackages#get(BeanFactory)}. These can
     * be used to define the security context of an {@link com.thoughtworks.xstream.XStream} instance.
     * <p>
     * This method will return the package names of the {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration}
     * annotated beans. After retrieval this method attaches a wildcard ({@code ".**"}) to the packages for XStream's
     * convenience.
     *
     * @param applicationContext the {@link ApplicationContext} to retrieve the auto-configuration base packages from
     * @return the auto-configuration base packages with {@code ".**"} appended to them
     */
    public static String[] autoConfigBasePackages(ApplicationContext applicationContext) {
        if (AutoConfigurationPackages.has(applicationContext)) {
            return AutoConfigurationPackages.get(applicationContext)
                                            .stream()
                                            .map(basePackage -> {
                                                logger.info("Constructing wildcard type for base package [{}].",
                                                            basePackage);
                                                return basePackage + PACKAGES_AND_SUBPACKAGES_WILDCARD;
                                            })
                                            .toArray(String[]::new);
        } else {
            logger.warn("Cannot extract types, because the provided ApplicationContext does not contain any @EnableAutoConfiguration annotated beans.");
            return new String[]{};
        }
    }

    private XStreamSecurityTypeUtility() {
        // Utility class
    }
}
