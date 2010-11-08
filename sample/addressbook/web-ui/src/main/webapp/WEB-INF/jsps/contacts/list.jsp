<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p><a href="${ctx}/contacts/new">Create a new contact</a></p>

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
            <td><a href="${ctx}/contacts/<c:out value='${contact.identifier}'/>">details</a> &nbsp;
                <a href="${ctx}/contacts/<c:out value='${contact.identifier}'/>/edit">edit</a> &nbsp;
                <a href="${ctx}/contacts/<c:out value='${contact.identifier}'/>/delete">delete</a></td>
        </tr>
    </c:forEach>
    </tbody>
</table>