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

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

public class OnSingleOverallCandidateCondition extends SpringBootCondition implements Condition {

    private static final String EXPECTED_ANNOTATION = ConditionalOnSingleOverallCandidate.class.getName();
    private static final boolean CONDITION_NOT_MET = false;
    private static final boolean CONDITION_MET = true;

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (metadata.isAnnotated(EXPECTED_ANNOTATION)) {
            MultiValueMap<String, Object> annotationAttributes =
                    metadata.getAllAnnotationAttributes(EXPECTED_ANNOTATION, true);
            Class conditionalOnClass = (Class) annotationAttributes.get("value").get(0);
            String[] beansOfConditionalClassType = context.getBeanFactory()
                                                          .getBeanNamesForType(conditionalOnClass);
            int numberOfBeans = beansOfConditionalClassType.length;
            return getConditionOutcome(numberOfBeans);
        }

        return new ConditionOutcome(CONDITION_NOT_MET, "");
    }

    private ConditionOutcome getConditionOutcome(int numberOfBeans) {
        if (numberOfBeans == 0) {
            return new ConditionOutcome(CONDITION_NOT_MET, "");
        } else if (numberOfBeans == 1) {
            return new ConditionOutcome(CONDITION_MET, "");
        } else {
            return new ConditionOutcome(CONDITION_NOT_MET, "");
        }
    }
}
