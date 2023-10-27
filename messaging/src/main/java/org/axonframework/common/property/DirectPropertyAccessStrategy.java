/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.common.property;

import java.lang.reflect.Field;

/**
 * Implementation of PropertyAccessStrategy that scans class hierarchy to get public field named "property"
 */
public class DirectPropertyAccessStrategy extends PropertyAccessStrategy {

	@Override
	protected int getPriority() {
		return -2048;
	}

	@Override
	protected <T> Property<T> propertyFor(Class<? extends T> targetClass, String property) {
		Field[] fields = targetClass.getFields();
		for(Field field : fields) {
			if (field.getName().equals(property)) {
				return new DirectlyAccessedProperty<>(field, property);
			}
		}
		return null;
	}
}
