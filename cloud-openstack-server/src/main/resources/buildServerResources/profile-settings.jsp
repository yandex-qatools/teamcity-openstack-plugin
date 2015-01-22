<%@ page import="jetbrains.buildServer.clouds.openstack.OpenstackCloudParameters" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="paramUrl" value="<%=OpenstackCloudParameters.ENDPOINT_URL%>"/>
<tr>
    <th><label for="${paramUrl}">Endpoint URL: <l:star/></label></th>
    <td><props:textProperty name="${paramUrl}" className="longField"/>
    </td>
</tr>

<c:set var="paramIdentity" value="<%=OpenstackCloudParameters.IDENTITY%>"/>
<tr>
    <th><label for="${paramIdentity}">Identity: <l:star/></label></th>
    <td><props:textProperty name="${paramIdentity}" className="longField"/>
    </td>
</tr>

<c:set var="paramPassword" value="<%=OpenstackCloudParameters.PASSWORD%>"/>
<tr>
    <th><label for="${paramPassword}">Password: <l:star/></label></th>
    <td><props:passwordProperty name="${paramPassword}" className="longField"/>
    </td>
</tr>

<c:set var="paramRegion" value="<%=OpenstackCloudParameters.REGION%>"/>
<tr>
    <th><label for="${paramRegion}">Region: <l:star/></label></th>
    <td><props:textProperty name="${paramRegion}" className="longField"/>
    </td>
</tr>

<c:set var="paramInstanceCap" value="<%=OpenstackCloudParameters.INSTANCE_CAP%>"/>
<tr>
    <th><label for="${paramInstanceCap}">Instance Cap: <l:star/></label></th>
    <td><props:textProperty name="${paramInstanceCap}" className="longField"/>
    </td>
</tr>

<c:set var="paramName" value="<%=OpenstackCloudParameters.IMAGES_PROFILES%>"/>
<tr>
    <th><label for="${paramName}">Agent images:</label></th>
    <td>
        <props:multilineProperty name="${paramName}" className="longField" linkTitle="Agent images to run" cols="55" rows="5" expanded="${true}"/>
    <span class="smallNote">
      YAML formatted list of agent images. i.e:<br/>
      my_teamcity_image:<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;image: ubuntu_trusty_14.04<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;flavor: m1.small<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;network: my_openstack_network<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;security_group: default<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;key_pair: my_username_keypair<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;*user_script: my_startup_script.sh<br/>
      &nbsp;&nbsp;&nbsp;&nbsp;*availability_zone: my_zone<br/>
      starred parameters are optional<br/>
      user_script should be located at teamcity server in directopy TEAMCITY_DATA_PATH/server/pluginData/openstack
    </span>
    </td>
</tr>
