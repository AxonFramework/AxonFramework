<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<table class="hor-minimalist-b">
    <thead>
    <tr>
        <th>id</th>
        <th>agg. ident.</th>
        <th>seq. nr.</th>
        <th>timestamp</th>
        <th>type</th>
        <%--<th>event</th>--%>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${events}" var="event">
        <tr>
            <td><c:out value='${event[0]}'/></td>
            <td><c:out value='${event[1]}'/></td>
            <td><c:out value='${event[2]}'/></td>
            <td><c:out value='${event[3]}'/></td>
            <td><c:out value='${event[4]}'/></td>
        </tr>
        <tr>
            <td colspan="5"><c:out value='${event[5]}'/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>