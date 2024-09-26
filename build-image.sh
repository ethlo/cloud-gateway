#!/bin/sh
mvn clean install
cd cloud-gateway-server
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=ethlocom/cloud-gateway:\$\{project.version}
