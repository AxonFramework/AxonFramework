<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<p>Change name for contact : <c:out value='${contact.name}'/></p>

<form:form commandName="contact">
    <form:errors path="*" cssClass="errorBox"/>
    <form:hidden path="identifier"/>
    <table>
          <tr>
              <td>Name:</td>
              <td><form:input path="name" /></td>
          </tr>
          <tr>
              <td colspan="2">
                  <input type="submit" name="submit" value="Submit" />
              </td>
          </tr>
      </table>
</form:form>