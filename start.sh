#!/usr/bin/env bash

export NOTARIZATION_APPLEID_USERNAME="appleid"
export NOTARIZATION_APPLEID_PASSWORD="appleid_password"

JAR_FILE=$(find . -name "quarkus-run.jar" | sort | tail -n 1)

"$(/usr/libexec/java_home -v 17)/bin/java" -Dquarkus.http.port=8383 \
	-Dquarkus.log.category.\"org.eclipse.cbi\".level=INFO \
	-Dquarkus.http.access-log.enabled=true \
	-Dquarkus.http.access-log.pattern=combined \
	-Dquarkus.http.access-log.log-to-file=true \
	-Dquarkus.http.access-log.log-directory="$(cd "$(dirname "${0}")" && pwd)/log" \
	-jar "${JAR_FILE}"