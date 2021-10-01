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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility class exposing an operation that searches the {@link ApplicationContext} for {@link ComponentScan} annotated
 * beans and adds the contained types and packages to an {@link XStream} instance.
 * <p>
 * The {@link ComponentScan} annotation on these beans defines the base classes and base packages of types used within a
 * Spring application. These thus reflect a reasonable default of types that {@link XStream} should allow.
 *
 * @author Steven van Beelen
 * @since 4.5.4
 */
public abstract class XStreamSecurityTypeUtility {

    private static final String PACKAGES_AND_SUBPACKAGES_WILDCARD = ".**";

    /**
     * Searches the {@link ApplicationContext} for {@link ComponentScan} annotated beans to allow the contained types
     * for the given {@code xStream} instance.
     * <p>
     * Will add the {@link ComponentScan#basePackageClasses()} and {@link ComponentScan#basePackages()} ({@link
     * ComponentScan#value() is used instead if not specified} to the given {@code xStream}, through {@link
     * XStream#allowTypes(Class[])} and {@link XStream#allowTypesByWildcard(String[])} respectively. If both the base
     * package classes and base packages of the {@link ComponentScan} are not set, the package of the {@link
     * ComponentScan} annotated bean is included.
     *
     * @param applicationContext the {@link ApplicationContext} to retrieve {@link ComponentScan} beans from
     * @param xStream            the {@link XStream} to set allow types and type wildcards for
     */
    public static void allowTypesFromComponentScanAnnotatedBeans(ApplicationContext applicationContext,
                                                                 XStream xStream) {
        applicationContext.getBeansWithAnnotation(ComponentScan.class)
                          .forEach((beanName, bean) -> {
                              ComponentScan ann =
                                      applicationContext.findAnnotationOnBean(beanName, ComponentScan.class);
                              if (!Objects.nonNull(ann)) {
                                  throw new IllegalArgumentException(
                                          "The ApplicationContext retrieved a bean of name [" + beanName + "] and type"
                                                  + " [" + bean.getClass() + "] for the ComponentScan annotation "
                                                  + "which does not contain the ComponentScan annotation."
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
                          });
    }

    private static boolean isEmptyAnnotation(ComponentScan ann) {
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
