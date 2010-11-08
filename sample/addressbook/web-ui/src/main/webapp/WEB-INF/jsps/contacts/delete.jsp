<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<p>Delete contact : <c:out value='${contact.name}'/></p>

<form:form commandName="contact">
    <form:errors path="*" cssClass="errorBox"/>
    <form:hidden path="identifier"/>
    <input type="submit" name="submit" value="Delete"/>
</form:form>