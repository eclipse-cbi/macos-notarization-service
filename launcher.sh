#!/usr/bin/env bash

export NOTARIZATION_APPLEID_USERNAME="appleid"
export NOTARIZATION_APPLEID_PASSWORD="appleid_password"

java -Dquarkus.http.port=8282 \
	-Dquarkus.log.category."org.eclipse.cbi".level=DEBUG \
	-jar target/macos-notarization-service-1.0-SNAPSHOT-runner.jar