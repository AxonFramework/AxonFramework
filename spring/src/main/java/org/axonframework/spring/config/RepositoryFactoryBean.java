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

 package org.axonframework.spring.config;

 import org.axonframework.config.AggregateConfiguration;
 import org.axonframework.modelling.command.Repository;
 import org.springframework.beans.factory.FactoryBean;

 /**
  * Spring Factory bean that allows registration of a Repository for an Aggregate in the Spring Application Context based
  * on a given AggregateConfiguration
  *
  * @param <T> The type of Aggregate stored by the repository
  * @author Allard Buijze
  * @since 3.3
  * @deprecated Replaced by the {@link SpringAggregateLookup} and {@link SpringAggregateConfigurer}.
  */
 @Deprecated
 public class RepositoryFactoryBean<T> implements FactoryBean<Repository<T>> {

     private final AggregateConfiguration<T> aggregateConfiguration;

     /**
      * Initialize the Factory Bean using given {@code aggregateConfiguration} to locate the configured Repository
      * instance.
      *
      * @param aggregateConfiguration The configuration providing access to the repository
      */
     public RepositoryFactoryBean(AggregateConfiguration<T> aggregateConfiguration) {
         this.aggregateConfiguration = aggregateConfiguration;
     }

     @Override
     public Repository<T> getObject() {
         return aggregateConfiguration.repository();
     }

     @Override
     public Class<?> getObjectType() {
         return Repository.class;
     }

     @Override
     public boolean isSingleton() {
         return true;
     }
 }
