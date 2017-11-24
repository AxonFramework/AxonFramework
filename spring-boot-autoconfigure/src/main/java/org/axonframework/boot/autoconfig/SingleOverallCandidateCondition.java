/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot.autoconfig;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SingleOverallCandidateCondition implements Condition {

    private static final String EXPECTED_ANNOTATION = ConditionalOnSingleOverallCandidate.class.getName();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (metadata.isAnnotated(EXPECTED_ANNOTATION)) {
            MultiValueMap<String, Object> annotationAttributes =
                    metadata.getAllAnnotationAttributes(EXPECTED_ANNOTATION, true);
            Class conditionalOnClass = (Class) annotationAttributes.get("value").get(0);

            List<String> matching = getMatchingBeans(
                    context,
                    new SingleCandidateBeanSearchSpec(context, metadata, ConditionalOnSingleOverallCandidate.class)
            );
            return !matching.isEmpty() && hasSingleAutowireCandidate(matching);
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private List<String> getMatchingBeans(ConditionContext context, BeanSearchSpec beans) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        if (beans.getStrategy() == SearchStrategy.PARENTS || beans.getStrategy() == SearchStrategy.ANCESTORS) {
            BeanFactory parent = beanFactory.getParentBeanFactory();

            Assert.isInstanceOf(ConfigurableListableBeanFactory.class, parent, "Unable to use SearchStrategy.PARENTS");

            beanFactory = (ConfigurableListableBeanFactory) parent;
        }

        if (beanFactory == null) {
            return Collections.emptyList();
        }

        List<String> beanNames = new ArrayList<>();
        boolean considerHierarchy = beans.getStrategy() != SearchStrategy.CURRENT;
        for (String beanName : beans.getNames()) {
            if (containsBean(beanFactory, beanName, considerHierarchy)) {
                beanNames.add(beanName);
            }
        }
        return beanNames;
    }

    private boolean containsBean(ConfigurableListableBeanFactory beanFactory,
                                 String beanName, boolean considerHierarchy) {
        if (considerHierarchy) {
            return beanFactory.containsBean(beanName);
        }
        return beanFactory.containsLocalBean(beanName);
    }

    private boolean hasSingleAutowireCandidate(List<String> beanNames) {
        return beanNames.size() == 1;
    }

    private static class BeanSearchSpec {

        private final Class<?> annotationType;

        private final List<String> names = new ArrayList<String>();

        private final List<String> types = new ArrayList<String>();

        private final List<String> annotations = new ArrayList<String>();

        private final List<String> ignoredTypes = new ArrayList<String>();

        private final SearchStrategy strategy;

        BeanSearchSpec(ConditionContext context, AnnotatedTypeMetadata metadata,
                       Class<?> annotationType) {
            this.annotationType = annotationType;
            MultiValueMap<String, Object> attributes = metadata
                    .getAllAnnotationAttributes(annotationType.getName(), true);
            collect(attributes, "name", this.names);
            collect(attributes, "value", this.types);
            collect(attributes, "type", this.types);
            collect(attributes, "annotation", this.annotations);
            collect(attributes, "ignored", this.ignoredTypes);
            collect(attributes, "ignoredType", this.ignoredTypes);
            this.strategy = (SearchStrategy) metadata
                    .getAnnotationAttributes(annotationType.getName()).get("search");
            BeanTypeDeductionException deductionException = null;
            try {
                if (this.types.isEmpty() && this.names.isEmpty()) {
                    addDeducedBeanType(context, metadata, this.types);
                }
            } catch (BeanTypeDeductionException ex) {
                deductionException = ex;
            }
            validate(deductionException);
        }

        protected void validate(BeanTypeDeductionException ex) {
            if (!hasAtLeastOne(this.types, this.names, this.annotations)) {
                String message = annotationName()
                        + " did not specify a bean using type, name or annotation";
                if (ex == null) {
                    throw new IllegalStateException(message);
                }
                throw new IllegalStateException(message + " and the attempt to deduce"
                                                        + " the bean's type failed", ex);
            }
        }

        private boolean hasAtLeastOne(List<?>... lists) {
            for (List<?> list : lists) {
                if (!list.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        protected String annotationName() {
            return "@" + ClassUtils.getShortName(this.annotationType);
        }

        protected void collect(MultiValueMap<String, Object> attributes, String key,
                               List<String> destination) {
            List<?> values = attributes.get(key);
            if (values != null) {
                for (Object value : values) {
                    if (value instanceof String[]) {
                        Collections.addAll(destination, (String[]) value);
                    } else {
                        destination.add((String) value);
                    }
                }
            }
        }

        private void addDeducedBeanType(ConditionContext context,
                                        AnnotatedTypeMetadata metadata, final List<String> beanTypes) {
            if (metadata instanceof MethodMetadata
                    && metadata.isAnnotated(Bean.class.getName())) {
                addDeducedBeanTypeForBeanMethod(context, (MethodMetadata) metadata,
                                                beanTypes);
            }
        }

        private void addDeducedBeanTypeForBeanMethod(ConditionContext context,
                                                     MethodMetadata metadata, final List<String> beanTypes) {
            try {
                // We should be safe to load at this point since we are in the
                // REGISTER_BEAN phase
                Class<?> returnType = ClassUtils.forName(metadata.getReturnTypeName(),
                                                         context.getClassLoader());
                beanTypes.add(returnType.getName());
            } catch (Throwable ex) {
                throw new BeanTypeDeductionException(metadata.getDeclaringClassName(),
                                                     metadata.getMethodName(), ex);
            }
        }

        public SearchStrategy getStrategy() {
            return (this.strategy != null ? this.strategy : SearchStrategy.ALL);
        }

        public List<String> getNames() {
            return this.names;
        }

        public List<String> getTypes() {
            return this.types;
        }

        public List<String> getAnnotations() {
            return this.annotations;
        }

        public List<String> getIgnoredTypes() {
            return this.ignoredTypes;
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder();
            string.append("(");
            if (!this.names.isEmpty()) {
                string.append("names: ");
                string.append(StringUtils.collectionToCommaDelimitedString(this.names));
                if (!this.types.isEmpty()) {
                    string.append("; ");
                }
            }
            if (!this.types.isEmpty()) {
                string.append("types: ");
                string.append(StringUtils.collectionToCommaDelimitedString(this.types));
            }
            string.append("; SearchStrategy: ");
            string.append(this.strategy.toString().toLowerCase());
            string.append(")");
            return string.toString();
        }
    }

    private static class SingleCandidateBeanSearchSpec extends BeanSearchSpec {

        SingleCandidateBeanSearchSpec(ConditionContext context,
                                      AnnotatedTypeMetadata metadata, Class<?> annotationType) {
            super(context, metadata, annotationType);
        }

        @Override
        protected void collect(MultiValueMap<String, Object> attributes, String key,
                               List<String> destination) {
            super.collect(attributes, key, destination);
            destination.removeAll(Arrays.asList("", Object.class.getName()));
        }

        @Override
        protected void validate(BeanTypeDeductionException ex) {
            Assert.isTrue(getTypes().size() == 1, annotationName() + " annotations must "
                    + "specify only one type (got " + getTypes() + ")");
        }
    }

    static final class BeanTypeDeductionException extends RuntimeException {

        private BeanTypeDeductionException(String className, String beanMethodName,
                                           Throwable cause) {
            super("Failed to deduce bean type for " + className + "." + beanMethodName,
                  cause);
        }
    }
}
