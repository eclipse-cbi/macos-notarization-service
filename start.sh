#!/usr/bin/env bash

export NOTARIZATION_APPLEID_USERNAME="appleid"
export NOTARIZATION_APPLEID_PASSWORD="appleid_password"

JAR_FILE=$(find . -name "target/macos-notarization-service*-runner.jar" | sort | tail -n 1)

"$(/usr/libexec/java_home -v 1.8)/bin/java" -Dquarkus.http.port=8080 \
	-Dquarkus.log.category.\"org.eclipse.cbi\".level=INFO \
	-jar "${JAR_FILE}"