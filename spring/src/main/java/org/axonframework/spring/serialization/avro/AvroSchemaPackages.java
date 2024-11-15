/*
 * Copyright (c) 2010-2024. Axon Framework
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
/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.serialization.avro;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Holder for package-based class scanning for Avro schema extraction.
 */
public class AvroSchemaPackages {
    private static final String BEAN = AvroSchemaPackages.class.getCanonicalName();
    private static final AvroSchemaPackages NONE = new AvroSchemaPackages();

    private final List<String> packages;

    @Nonnull
    public static AvroSchemaPackages get(BeanFactory beanFactory) {
        try {
            return beanFactory.getBean(BEAN, AvroSchemaPackages.class);
        } catch (NoSuchBeanDefinitionException e) {
            return NONE;
        }
    }

    AvroSchemaPackages(String... packages) {
        this.packages = Arrays.stream(packages).filter(StringUtils::hasText).collect(Collectors.toList());
    }

    public List<String> getPackages() {
        return packages;
    }

    static class Registrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;

        Registrar(Environment environment) {
            this.environment = environment;
        }

        public static void register(@Nonnull BeanDefinitionRegistry registry, @Nonnull Set<String> packageNames) {
            if (registry.containsBeanDefinition(BEAN)) {
                AvroSchemaScanPackagesBeanDefinition beanDefinition = (AvroSchemaScanPackagesBeanDefinition)registry.getBeanDefinition(BEAN);
                beanDefinition.addPackageNames(packageNames);
            } else {
                registry.registerBeanDefinition(BEAN, new AvroSchemaScanPackagesBeanDefinition(packageNames));
            }
        }

        @Override
        public void registerBeanDefinitions(@Nonnull AnnotationMetadata importingClassMetadata, @Nonnull BeanDefinitionRegistry registry) {
            register(registry, getPackagesToScan(importingClassMetadata));
        }

        private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
            AnnotationAttributes attributes = Objects.requireNonNull(AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(AvroSchemaScan.class.getName())));
            List<String> basePackagesToScan = Arrays.stream(attributes.getStringArray("basePackages")).map(basePackage ->
                    Arrays.asList(StringUtils.tokenizeToStringArray(
                            environment.resolvePlaceholders(basePackage),
                            ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS
                        )
                    )).collect(Collectors.toList())
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
            List<String> baseClassPackagesToScan = Arrays.stream(attributes.getClassArray("basePackageClasses")).map( basePackageClass ->
                environment.resolvePlaceholders(ClassUtils.getPackageName(basePackageClass))
                ).collect(Collectors.toList());
            List<String> packagesToScan = Stream.concat(baseClassPackagesToScan.stream(), basePackagesToScan.stream())
                .collect(Collectors.toList());
            if (packagesToScan.isEmpty()) {
                String defaultPackageName = ClassUtils.getPackageName(metadata.getClassName());
                Assert.state(StringUtils.hasLength(defaultPackageName), "@"+ AvroSchemaScan.class.getName() + " cannot be used with the default package");
                return Collections.singleton(defaultPackageName);
            }
            return new HashSet<>(packagesToScan);
        }
    }

    static class AvroSchemaScanPackagesBeanDefinition extends GenericBeanDefinition {
        private final LinkedHashSet<String> packageNames = new LinkedHashSet<>();
        AvroSchemaScanPackagesBeanDefinition(Collection<String> packageNames) {
            super();
            setBeanClass(AvroSchemaPackages.class);
            setRole(ROLE_INFRASTRUCTURE);
            addPackageNames(packageNames);
        }

        public void addPackageNames(Collection<String> packageNames) {
            this.packageNames.addAll(packageNames);
            getConstructorArgumentValues().addIndexedArgumentValue(0, StringUtils.toStringArray(this.packageNames));
        }
    }
}
