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

import com.thoughtworks.xstream.XStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class exposing an operation that searches the {@link ApplicationContext} for entity types and packages and
 * adds them to an {@link XStream} instance. It prefers {@link EntityScan} annotated beans. In absence of these it
 * defaults to the output from the {@link AutoConfigurationPackages#get(BeanFactory)} method.
 * <p>
 * The {@link EntityScan} annotation on these beans defines the base classes and base packages of types used within a
 * Spring application. These thus reflect a reasonable default of types that {@link XStream} should allow.
 *
 * @author Steven van Beelen
 * @since 4.5.4
 */
public abstract class XStreamSecurityTypeUtility {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PACKAGES_AND_SUBPACKAGES_WILDCARD = ".**";

    /**
     * Searches the {@link ApplicationContext} for entity types or package names to allow for the given {@code xStream}
     * instance. This method prefers {@link EntityScan} annotated beans. In absence of the {@code EntityScan} we use the
     * output from {@link AutoConfigurationPackages#get(BeanFactory)}.
     * <p>
     * Will add the {@link EntityScan#basePackageClasses()} and {@link EntityScan#basePackages()} ({@link
     * EntityScan#value() is used instead if not specified} to the given {@code xStream}, through {@link
     * XStream#allowTypes(Class[])} and {@link XStream#allowTypesByWildcard(String[])} respectively. If both the base
     * package classes and base packages of the {@code EntityScan} are not set, the package of the {@code EntityScan}
     * annotated bean is included.
     * <p>
     * We use the {@code AutoConfigurationPackages#get(BeanFactory)} outcome as the default, since Spring Boot uses this
     * too. This default will include the package names of {@link javax.persistence.Entity} and {@link
     * org.springframework.boot.autoconfigure.EnableAutoConfiguration} annotated beans.
     *
     * @param applicationContext the {@link ApplicationContext} to retrieve {@link EntityScan} beans from
     * @param xStream            the {@link XStream} to set allow types and type wildcards for
     */
    public static void allowEntityTypesFrom(ApplicationContext applicationContext, XStream xStream) {
        Map<String, Object> entityScanAnnotatedBeans = applicationContext.getBeansWithAnnotation(EntityScan.class);
        if (!entityScanAnnotatedBeans.isEmpty()) {
            entityScanAnnotatedBeans.forEach(
                    (beanName, bean) -> extractAllowedTypesFrom(beanName, bean, applicationContext, xStream)
            );
        } else {
            extractAllowedTypesFromAutoConfigPackages(applicationContext, xStream);
        }
    }

    private static void extractAllowedTypesFromAutoConfigPackages(ApplicationContext applicationContext,
                                                                  XStream xStream) {
        if (AutoConfigurationPackages.has(applicationContext)) {
            allowPackages(xStream, AutoConfigurationPackages.get(applicationContext).toArray(new String[]{}));
        } else {
            logger.warn("Cannot extract allowed types for XStream, because the provided ApplicationContext "
                                + "does not contain any @EnableAutoConfiguration annotated beans.");
        }
    }

    private static void extractAllowedTypesFrom(String beanName,
                                                Object bean,
                                                ApplicationContext applicationContext,
                                                XStream xStream) {
        EntityScan ann = applicationContext.findAnnotationOnBean(beanName, EntityScan.class);
        if (!Objects.nonNull(ann)) {
            throw new IllegalArgumentException(
                    "The ApplicationContext retrieved a bean of name [" + beanName + "] and type"
                            + " [" + bean.getClass() + "] for the EntityScan annotation "
                            + "which does not contain the EntityScan annotation."
            );
        }

        if (isEmptyAnnotation(ann)) {
            xStream.allowTypesByWildcard(new String[]{
                    bean.getClass().getPackage().getName() + PACKAGES_AND_SUBPACKAGES_WILDCARD
            });
        } else {
            xStream.allowTypes(ann.basePackageClasses());
            if (ann.basePackages().length != 0) {
                allowPackages(xStream, ann.basePackages());
            } else {
                allowPackages(xStream, ann.value());
            }
        }
    }

    private static boolean isEmptyAnnotation(EntityScan ann) {
        return ann.basePackageClasses().length == 0 && ann.basePackages().length == 0 && ann.value().length == 0;
    }

    private static void allowPackages(XStream xStream, String[] basePackages) {
        String[] typeWildcards = Arrays.stream(basePackages)
                                       .map(basePackage -> basePackage + PACKAGES_AND_SUBPACKAGES_WILDCARD)
                                       .toArray(String[]::new);
        xStream.allowTypesByWildcard(typeWildcards);
    }

    private XStreamSecurityTypeUtility() {
        // Utility class
    }
}
