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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Holder for package-based class scanning for Avro schema extraction.
 */
public class AvroSchemaPackages {

    private static final String BEAN = AvroSchemaPackages.class.getCanonicalName();
    private static final AvroSchemaPackages NONE = new AvroSchemaPackages();

    private final List<String> packages;

    /**
     * Loader for the {@link AvroSchemaPackages} bean.
     *
     * @param beanFactory bean factory.
     * @return registered bean or empty null-object.
     */
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

    /**
     * Retrieves packages to scan for schemas.
     *
     * @return packages to scan.
     */
    public List<String> getPackages() {
        return packages;
    }

    /**
     * Registrar detecting {@link AvroSchemaScan} annotations and registering {@link AvroSchemaPackages} bean holding
     * the packages to scan for Avro schemas.
     */
    static class Registrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;

        Registrar(@Nonnull Environment environment) {
            Assert.notNull(environment, "Environment must not be null");
            this.environment = environment;
        }

        public static void register(@Nonnull BeanDefinitionRegistry registry, @Nonnull Set<String> packageNames) {
            Assert.notNull(registry, "Registry must not be null");
            Assert.notNull(packageNames, "PackageNames must not be null");
            if (registry.containsBeanDefinition(BEAN)) {
                AvroSchemaScanPackagesBeanDefinition beanDefinition = (AvroSchemaScanPackagesBeanDefinition) registry.getBeanDefinition(
                        BEAN);
                beanDefinition.addPackageNames(packageNames);
            } else {
                registry.registerBeanDefinition(BEAN, new AvroSchemaScanPackagesBeanDefinition(packageNames));
            }
        }

        public static Set<String> getPackagesToScan(
                Environment environment,
                AnnotationMetadata metadata,
                String annotationClassName,
                String annotationAttributePackages,
                String annotationAttributePackageClasses
        ) {
            AnnotationAttributes attributes = Objects.requireNonNull(
                    AnnotationAttributes.fromMap(
                            metadata.getAnnotationAttributes(annotationClassName)
                    )
            );
            Set<String> packagesToScan = new LinkedHashSet<>();
            for (String basePackage : attributes.getStringArray(annotationAttributePackages)) {
                String[] tokenized = StringUtils.tokenizeToStringArray(
                        environment.resolvePlaceholders(basePackage),
                        ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
                Collections.addAll(packagesToScan, tokenized);
            }
            for (Class<?> basePackageClass : attributes.getClassArray(annotationAttributePackageClasses)) {
                packagesToScan.add(environment.resolvePlaceholders(ClassUtils.getPackageName(basePackageClass)));
            }
            if (packagesToScan.isEmpty()) {
                String packageName = ClassUtils.getPackageName(metadata.getClassName());
                Assert.state(StringUtils.hasLength(packageName),
                             "@" + annotationClassName + " cannot be used with the default package");
                return Collections.singleton(packageName);
            }
            return packagesToScan;
        }

        @Override
        public void registerBeanDefinitions(@Nonnull AnnotationMetadata importingClassMetadata,
                                            @Nonnull BeanDefinitionRegistry registry) {
            register(
                    registry,
                    getPackagesToScan(
                            this.environment,
                            importingClassMetadata,
                            AvroSchemaScan.class.getName(),
                            "basePackages",
                            "basePackageClasses"
                    )
            );
        }
    }

    /**
     * Bean definition for {@link AvroSchemaPackages}.
     */
    static class AvroSchemaScanPackagesBeanDefinition extends GenericBeanDefinition {

        private final LinkedHashSet<String> packageNames = new LinkedHashSet<>();

        AvroSchemaScanPackagesBeanDefinition(Collection<String> packageNames) {
            super();
            setBeanClass(AvroSchemaPackages.class);
            setRole(ROLE_INFRASTRUCTURE);
            addPackageNames(packageNames);
        }

        @Override
        public Supplier<?> getInstanceSupplier() {
            return () -> new AvroSchemaPackages(StringUtils.toStringArray(this.packageNames));
        }

        public void addPackageNames(Collection<String> packageNames) {
            this.packageNames.addAll(packageNames);
            getConstructorArgumentValues().addIndexedArgumentValue(0, StringUtils.toStringArray(this.packageNames));
        }
    }
}
