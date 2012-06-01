/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventstore.jpa.criteria;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class JpaCriteriaBuilderTest {

    @Test
    public void testBuildCriteria_ComplexStructureWithUnequalNull() throws Exception {
        JpaCriteriaBuilder builder = new JpaCriteriaBuilder();
        JpaCriteria criteria = (JpaCriteria) builder.property("property").lessThan("less")
                                                    .and(builder.property("property2").greaterThan("gt"))
                                                    .or(builder.property("property3").notIn(builder.property("collection")))
                                                    .or(builder.property("property4").isNot(null));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < :param0) AND (entry.property2 > :param1)) OR (entry.property3 NOT IN entry.collection)) OR (entry.property4 IS NOT NULL)",
                query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get("param0"));
        assertEquals("gt", parameters.getParameters().get("param1"));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithUnequalValue() throws Exception {
        JpaCriteriaBuilder builder = new JpaCriteriaBuilder();
        JpaCriteria criteria = (JpaCriteria) builder.property("property").lessThan("less")
                                                    .and(builder.property("property2").greaterThanEquals("gte"))
                                                    .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                                                    .or(builder.property("property4").isNot("4"));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < :param0) AND (entry.property2 >= :param1)) OR (entry.property3 IN (:param2))) OR (entry.property4 <> :param3)",
                query.toString());
        assertEquals(4, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get("param0"));
        assertEquals("gte", parameters.getParameters().get("param1"));
        assertEquals("4", parameters.getParameters().get("param3"));
        assertArrayEquals(new String[]{"piet", "klaas"}, (Object[]) parameters.getParameters().get("param2"));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithUnequalProperty() throws Exception {
        JpaCriteriaBuilder builder = new JpaCriteriaBuilder();
        JpaCriteria criteria = (JpaCriteria) builder.property("property").lessThanEquals("lte")
                                                    .and(builder.property("property2").greaterThanEquals("gte"))
                                                    .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                                                    .or(builder.property("property4").isNot(builder.property("property4")));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property <= :param0) AND (entry.property2 >= :param1)) OR (entry.property3 IN (:param2))) OR (entry.property4 <> entry.property4)",
                query.toString());
        assertEquals(3, parameters.getParameters().size());
        assertEquals("lte", parameters.getParameters().get("param0"));
        assertEquals("gte", parameters.getParameters().get("param1"));
        assertArrayEquals(new String[]{"piet", "klaas"}, (Object[]) parameters.getParameters().get("param2"));
    }
    @Test
    public void testBuildCriteria_ComplexStructureWithEqualNull() throws Exception {
        JpaCriteriaBuilder builder = new JpaCriteriaBuilder();
        JpaCriteria criteria = (JpaCriteria) builder.property("property").lessThan("less")
                                                    .and(builder.property("property2").greaterThanEquals("gte"))
                                                    .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                                                    .or(builder.property("property4").is(null));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < :param0) AND (entry.property2 >= :param1)) OR (entry.property3 IN (:param2))) OR (entry.property4 IS NULL)",
                query.toString());
        assertEquals(3, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get("param0"));
        assertEquals("gte", parameters.getParameters().get("param1"));
        assertArrayEquals(new String[]{"piet", "klaas"}, (Object[]) parameters.getParameters().get("param2"));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithEqualValue() throws Exception {
        JpaCriteriaBuilder builder = new JpaCriteriaBuilder();
        JpaCriteria criteria = (JpaCriteria) builder.property("property").lessThan("less")
                                                    .and(builder.property("property2").greaterThanEquals("gte"))
                                                    .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                                                    .or(builder.property("property4").is("4"));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < :param0) AND (entry.property2 >= :param1)) OR (entry.property3 IN (:param2))) OR (entry.property4 = :param3)",
                query.toString());
        assertEquals(4, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get("param0"));
        assertEquals("gte", parameters.getParameters().get("param1"));
        assertEquals("4", parameters.getParameters().get("param3"));
        assertArrayEquals(new String[]{"piet", "klaas"}, (Object[]) parameters.getParameters().get("param2"));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithEqualProperty() throws Exception {
        JpaCriteriaBuilder builder = new JpaCriteriaBuilder();
        JpaCriteria criteria = (JpaCriteria) builder.property("property").lessThan(builder.property("prop1"))
                                                    .and(builder.property("property2").greaterThanEquals("gte"))
                                                    .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                                                    .or(builder.property("property4").is(builder.property("property4")));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < entry.prop1) AND (entry.property2 >= :param0)) OR (entry.property3 IN (:param1))) OR (entry.property4 = entry.property4)",
                query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals("gte", parameters.getParameters().get("param0"));
        assertArrayEquals(new String[]{"piet", "klaas"}, (Object[]) parameters.getParameters().get("param1"));
    }}
