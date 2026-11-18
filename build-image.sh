#!/bin/sh
mvn clean install -DskipTests
cd cloud-gateway-server
mvn spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=ghcr.io/ethlo/cloud-gateway:\$\{project.version}
