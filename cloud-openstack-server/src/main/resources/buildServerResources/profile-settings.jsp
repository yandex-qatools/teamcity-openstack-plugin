<%@ page import="jetbrains.buildServer.clouds.openstack.OpenstackCloudParameters" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="paramUrl" value="<%=OpenstackCloudParameters.INDENTITY_URL%>"/>
<tr>
    <th><label for="${paramUrl}">Indentity URL: <l:star/></label></th>
    <td><props:textProperty name="${paramUrl}" className="longField"/>
    </td>
</tr>

<c:set var="paramUsername" value="<%=OpenstackCloudParameters.USERNAME%>"/>
<tr>
    <th><label for="${paramUsername}">Username: <l:star/></label></th>
    <td><props:textProperty name="${paramUsername}" className="longField"/>
    </td>
</tr>

<c:set var="paramPassword" value="<%=OpenstackCloudParameters.PASSWORD%>"/>
<tr>
    <th><label for="${paramPassword}">Password: <l:star/></label></th>
    <td><props:passwordProperty name="${paramPassword}" className="longField"/>
    </td>
</tr>

<c:set var="paramTenant" value="<%=OpenstackCloudParameters.TENANT%>"/>
<tr>
    <th><label for="${paramTenant}">Tenant: <l:star/></label></th>
    <td><props:textProperty name="${paramTenant}" className="longField"/>
    </td>
</tr>

<c:set var="paramName" value="<%=OpenstackCloudParameters.IMAGES_PROFILE_SETTING%>"/>
<tr>
    <th><label for="${paramName}">Agent images:</label></th>
    <td>
        <props:multilineProperty name="${paramName}" className="longField" linkTitle="Agent images to run" cols="55" rows="5" expanded="${true}"/>
    <span class="smallNote">
      YAML formatted list of agent images. i.e:<br/>
      images:<br/>
      - name: 1<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;image_id: 2<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;hardware_id: 3<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;network: 4<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;security_group: 5<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;key_pair: 6<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;zone: 6<br/>
    </span>
    </td>
</tr>
