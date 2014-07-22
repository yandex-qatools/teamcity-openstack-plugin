<%@ page import="jetbrains.buildServer.clouds.openstack.OpenstackCloudParameters" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="paramUrl" value="<%=OpenstackCloudParameters.ENDPOINT_URL%>"/>
<tr>
    <th><label for="${paramUrl}">Endpoint URL: <l:star/></label></th>
    <td><props:textProperty name="${paramUrl}" className="longField"/>
    </td>
</tr>

<c:set var="paramUsername" value="<%=OpenstackCloudParameters.IDENTITY%>"/>
<tr>
    <th><label for="${paramUsername}">Identity: <l:star/></label></th>
    <td><props:textProperty name="${paramUsername}" className="longField"/>
    </td>
</tr>

<c:set var="paramPassword" value="<%=OpenstackCloudParameters.PASSWORD%>"/>
<tr>
    <th><label for="${paramPassword}">Password: <l:star/></label></th>
    <td><props:passwordProperty name="${paramPassword}" className="longField"/>
    </td>
</tr>

<c:set var="paramZone" value="<%=OpenstackCloudParameters.ZONE%>"/>
<tr>
    <th><label for="${paramZone}">Region: <l:star/></label></th>
    <td><props:textProperty name="${paramZone}" className="longField"/>
    </td>
</tr>

<c:set var="paramName" value="<%=OpenstackCloudParameters.IMAGES_PROFILE_SETTING%>"/>
<tr>
    <th><label for="${paramName}">Agent images:</label></th>
    <td>
        <props:multilineProperty name="${paramName}" className="longField" linkTitle="Agent images to run" cols="55" rows="5" expanded="${true}"/>
    <span class="smallNote">
      YAML formatted list of agent images. i.e:<br/>
      my_teamcity_image:<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;image: 1<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;flavor: 2<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;network: 3<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;security_group: 4<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;key_pair: 5<br/>
    </span>
    </td>
</tr>
