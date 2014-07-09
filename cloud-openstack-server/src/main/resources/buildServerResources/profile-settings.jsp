<%@ page import="jetbrains.buildServer.clouds.openstack.OpenstackCloudConstants" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="paramName" value="<%=OpenstackCloudConstants.IMAGES_PROFILE_SETTING%>"/>

<tr>
  <th><label for="${paramName}">Agent images:</label></th>
  <td>
    <props:multilineProperty name="${paramName}" className="longField" linkTitle="Agent images to run" cols="55" rows="5" expanded="${true}"/>
    <span class="smallNote">
      List of agent images, each on new line.
      <br/>
      Image format: <strong>&lt;Image name&gt;@&lt;server-local path to agent installation folder&gt;</strong>
      <br/>
      Additional settins are specified in the following format:
      <br/>
      <strong>@@&lt;Image name&gt;:&lt;parameter&gt;</strong>,
      <br/>
      where parameter could be:
      <br/>
      <strong>reuse</strong> to enable agent copies re-use or
      <br/>
      <strong>prop:&lt;agent property&gt;=&lt;value&gt;</strong> additional property for <em>buildAgent.configuration</em> file
    </span>
  </td>
</tr>
