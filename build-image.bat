@echo off
mvn clean && mvn spring-boot:build-image -Dspring-boot.build-image.imageName=ethlocom/cloud-gateway -DskipTests
