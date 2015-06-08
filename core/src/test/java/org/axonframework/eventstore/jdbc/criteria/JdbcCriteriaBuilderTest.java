package org.axonframework.eventstore.jdbc.criteria;

 import org.junit.*;

 import java.util.List;

 import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class JdbcCriteriaBuilderTest {

    @Test
    public void testParameterTypeRetained() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria =  (JdbcCriteria)builder.property("property").lessThan(1.0d)
                .and(builder.property("property2").is(1L))
                .or(builder.property("property3").isNot(1))
                .or(builder.property("property4").is("1"));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);

        List<Object> params = parameters.getParameters();
        assertTrue(params.get(0) instanceof Double);
        final Object param1 = params.get(1);
        assertTrue(param1 instanceof Long);
        assertTrue(params.get(2) instanceof Integer);
        assertTrue(params.get(3) instanceof String);
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithUnequalNull() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").lessThan("less")
                .and(builder.property("property2").greaterThan("gt"))
                .or(builder.property("property3").notIn(builder.property("collection")))
                .or(builder.property("property4").isNot(null));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < ?) AND (entry.property2 > ?)) OR (entry.property3 NOT IN entry.collection)) OR (entry.property4 IS NOT NULL)",
                query.toString());
        assertEquals(2, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get(0));
        assertEquals("gt", parameters.getParameters().get(1));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithUnequalValue() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").lessThan("less")
                .and(builder.property("property2").greaterThanEquals("gte"))
                .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                .or(builder.property("property4").isNot("4"));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < ?) AND (entry.property2 >= ?)) OR (entry.property3 IN (?,?))) OR (entry.property4 <> ?)",
                query.toString());
        assertEquals(5, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get(0));
        assertEquals("gte", parameters.getParameters().get(1));
        assertEquals("4", parameters.getParameters().get(4));
        assertEquals("piet", parameters.getParameters().get(2));
        assertEquals("klaas", parameters.getParameters().get(3));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithUnequalProperty() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").lessThanEquals("lte")
                .and(builder.property("property2").greaterThanEquals("gte"))
                .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                .or(builder.property("property4").isNot(builder.property("property4")));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property <= ?) AND (entry.property2 >= ?)) OR (entry.property3 IN (?,?))) OR (entry.property4 <> entry.property4)",
                query.toString());
        assertEquals(4, parameters.getParameters().size());
        assertEquals("lte", parameters.getParameters().get(0));
        assertEquals("gte", parameters.getParameters().get(1));
        assertEquals("piet", parameters.getParameters().get(2));
        assertEquals("klaas", parameters.getParameters().get(3));
    }
    @Test
    public void testBuildCriteria_ComplexStructureWithEqualNull() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").lessThan("less")
                .and(builder.property("property2").greaterThanEquals("gte"))
                .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                .or(builder.property("property4").is(null));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < ?) AND (entry.property2 >= ?)) OR (entry.property3 IN (?,?))) OR (entry.property4 IS NULL)",
                query.toString());
        assertEquals(4, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get(0));
        assertEquals("gte", parameters.getParameters().get(1));
        assertEquals("piet", parameters.getParameters().get(2));
        assertEquals("klaas", parameters.getParameters().get(3));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithEqualValue() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").lessThan("less")
                .and(builder.property("property2").greaterThanEquals("gte"))
                .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                .or(builder.property("property4").is("4"));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < ?) AND (entry.property2 >= ?)) OR (entry.property3 IN (?,?))) OR (entry.property4 = ?)",
                query.toString());
        assertEquals(5, parameters.getParameters().size());
        assertEquals("less", parameters.getParameters().get(0));
        assertEquals("gte", parameters.getParameters().get(1));
        assertEquals("4", parameters.getParameters().get(4));
        assertEquals("piet", parameters.getParameters().get(2));
        assertEquals("klaas", parameters.getParameters().get(3));
    }

    @Test
    public void testBuildCriteria_ComplexStructureWithEqualProperty() throws Exception {
        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("property").lessThan(builder.property("prop1"))
                .and(builder.property("property2").greaterThanEquals("gte"))
                .or(builder.property("property3").in(new String[]{"piet", "klaas"}))
                .or(builder.property("property4").is(builder.property("property4")));

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("entry", query, parameters);
        assertEquals(
                "(((entry.property < entry.prop1) AND (entry.property2 >= ?)) OR (entry.property3 IN (?,?))) OR (entry.property4 = entry.property4)",
                query.toString());
        assertEquals(3, parameters.getParameters().size());
        assertEquals("gte", parameters.getParameters().get(0));
        assertEquals("piet", parameters.getParameters().get(1));
        assertEquals("klaas", parameters.getParameters().get(2));
    }}
