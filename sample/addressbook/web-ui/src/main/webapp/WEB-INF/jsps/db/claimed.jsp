<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<table class="hor-minimalist-b">
    <thead>
    <tr>
        <th>Claimed Name</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${claimedNames}" var="claimedName">
        <tr>
            <td><c:out value='${claimedName.contactName}'/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>