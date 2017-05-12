TEAMCITY_VERSION=10.0.3


all:

tsinstall:
	apt-get install teamcity-server

deps:
	mvn install:install-file -Dfile=/usr/local/teamcity-10.0.3/webapps/ROOT/WEB-INF/lib/cloud-interface.jar -DgroupId=jetbrains.buildServer.clouds -DartifactId=cloud-interface -Dversion=10.0.3 -Dpackaging=jar
	mvn install:install-file -Dfile=/usr/local/teamcity-10.0.3/webapps/ROOT/WEB-INF/lib/cloud-shared.jar -DgroupId=jetbrains.buildServer.clouds -DartifactId=cloud-shared -Dversion=10.0.3 -Dpackaging=jar

pure_build:
	mvn clean package

build: tsinstall deps pure_build
