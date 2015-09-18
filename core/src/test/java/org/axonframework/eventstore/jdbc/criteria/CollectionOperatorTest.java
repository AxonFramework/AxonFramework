package org.axonframework.eventstore.jdbc.criteria;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Rene de Waele
 */
public class CollectionOperatorTest {

    @Test
    public void testInArrayOfObjects() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").in(new String[]{"piet", "klaas"});
        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals("entry.property IN (?,?)", query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals("piet", parameters.getParameters().get(0));
        assertEquals("klaas", parameters.getParameters().get(1));
    }

    @Test
    public void testInArrayOfPrimitives() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").in(new int[]{1, 5});
        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals("entry.property IN (?,?)", query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals(1, parameters.getParameters().get(0));
        assertEquals(5, parameters.getParameters().get(1));
    }

    @Test
    public void testInSingleValue() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").in(10);
        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals("entry.property IN (?)", query.toString());
        assertEquals(1, parameters.getParameters().size());
        assertEquals(10, parameters.getParameters().get(0));
    }

    @Test
    public void testInNull() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").in(null);
        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals("entry.property IN (?)", query.toString());
        assertEquals(1, parameters.getParameters().size());
        assertNull(parameters.getParameters().get(0));
    }

    @Test
    public void testInCollection() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").in(Arrays.asList("piet", "klaas"));
        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals("entry.property IN (?,?)", query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals("piet", parameters.getParameters().get(0));
        assertEquals("klaas", parameters.getParameters().get(1));
    }

    @Test
    public void testNotIn() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").notIn(new String[]{"piet", "klaas"});
        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals("entry.property NOT IN (?,?)", query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals("piet", parameters.getParameters().get(0));
        assertEquals("klaas", parameters.getParameters().get(1));
    }

}