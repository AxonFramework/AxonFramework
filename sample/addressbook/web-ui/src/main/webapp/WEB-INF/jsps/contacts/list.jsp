<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<table class="hor-minimalist-b">
    <thead>
    <tr>
        <th>Name</th>
        <th>actions</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${contacts}" var="contact">
        <tr>
            <td><c:out value='${contact.name}'/></td>
            <td><a href="${ctx}/contacts/<c:out value='${contact.identifier}'/>">details</a></td>
        </tr>
    </c:forEach>
    </tbody>
</table>