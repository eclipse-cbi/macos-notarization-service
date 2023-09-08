# macOS Notarization Service

[![Build Status](https://ci.eclipse.org/cbi/buildStatus/icon?job=macos-notarization-service%2Fmain)](https://ci.eclipse.org/cbi/job/macos-notarization-service/job/main/) 
[![GitHub release](https://img.shields.io/github/release/eclipse-cbi/macos-notarization-service.svg?label=release)](https://github.com/eclipse-cbi/macos-notarization-service.svg/releases/latest)
[![GitHub license](https://img.shields.io/github/license/eclipse-cbi/macos-notarization-service.svg)](https://github.com/eclipse-cbi/macos-notarization-service/blob/master/LICENSE)

This is a web service that runs on macOS and offers a REST API to notarize signed application bundles or signed DMG. You can read more about notarization on the [Apple developer website](https://developer.apple.com/documentation/security/notarizing_your_app_before_distribution).

## Getting started

### Requirements

* Java 17+. We advise you use the [AdoptOpenJDK](https://adoptopenjdk.net) binaries via [homebrew](https://brew.sh)

```bash
    brew install openjdk@17 
```

### Build

    ./mvnw clean package
    
It produces 2 jar files in `/target`:

* `macos-notarization-service-1.0-SNAPSHOT.jar` - containing just the classes and resources of the projects, it’s the regular artifact produced by the Maven build;

* `macos-notarization-service-1.0-SNAPSHOT-runner.jar` - being an executable jar. Be aware that it’s not an über-jar as the dependencies are copied into the target/lib directory.

You can run the application using: `java -jar target/macos-notarization-service-1.0-SNAPSHOT-runner.jar`

The Class-Path entry of the `MANIFEST.MF` from the runner jar explicitly lists the jars from the lib directory. So if you want to deploy your application somewhere, you need to copy the runner jar as well as the lib directory.

See below for advanced startup method.

### Run

Configure `NOTARIZATION_APPLEID_USERNAME` and `NOTARIZATION_APPLEID_PASSWORD` with the proper values for your Apple developer ID account in file `start.sh`. Then, just run

    ./start.sh

On production system, it is advised to run this service as a system daemon with `launchd`. The service will then be started automatically at boot time or if the program crash. You can find a sample file to edit and put in `/Library/LaunchDaemons` in [src/main/launchd/org.eclipse.cbi.macos-notarization-service.plist](https://github.com/eclipse-cbi/macos-notarization-service/blob/master/src/main/launchd/org.eclipse.cbi.macos-notarization-service.plist). To load (or unload) the service, just do

    sudo launchctl load -w /Library/LaunchDaemons/org.eclipse.cbi.macos-notarization-service.plist

or

    sudo launchctl unload -w /Library/LaunchDaemons/org.eclipse.cbi.macos-notarization-service.plist

See [Apple documentation](https://developer.apple.com/library/archive/documentation/MacOSX/Conceptual/BPSystemStartup/Chapters/Introduction.html#//apple_ref/doc/uid/10000172i-SW1-SW1) about daemons and agents for more options.

## Documentation

The service is [Quarkus](https://quarkus.io) application exposing a simple REST API with 3 endpoints. See [Quarkus documentation](https://quarkus.io/guides/all-config) for all of its configuration options. 

The following script calls the 3 endpoints successively to notarize a signed DMG file. It assumes that `jq` is installed on the system running the script. The notarization service is expected to run on IP `10.0.0.1` on port `8383`.

```bash
DMG="/path/to/myApp.dmg"

RESPONSE=\
$(curl -s -X POST \
  -F file=@${DMG} \
  -F 'options={"primaryBundleId": "my-primary-bundle-id", "staple": true};type=application/json' \
  http://10.0.0.1:8383/macos-notarization-service/notarize)
  
UUID=$(echo ${RESPONSE} | jq -r '.uuid')

STATUS=$(echo ${RESPONSE} | jq -r '.notarizationStatus.status')

while [[ ${STATUS} == 'IN_PROGRESS' ]]; do
  sleep 1m
  RESPONSE=$(curl -s http://10.0.0.1:8383/macos-notarization-service/${UUID}/status)
  STATUS=$(echo ${RESPONSE} | jq -r '.notarizationStatus.status')
done

if [[ ${STATUS} != 'COMPLETE' ]]; then
  echo "Notarization failed: ${RESPONSE}"
  exit 1
fi

mv "${DMG}" "unnotarized-${DMG}"

curl -JO http://10.0.0.1:8383/macos-notarization-service/${UUID}/download
```

We first upload our DMG to the service endpoint `macos-notarization-service/notarize` in a form part named `file` along with a set of options in JSON format in a form part named `options`. Only two options are available for now:

 * **primaryBundleId** (required): the primary bundle ID that will be sent to the notarization service by `xcrun altool`. The value you give doesn’t need to match the bundle identifier of the submitted app or have any particular value. It only needs to make sense to you. See [Apple documentation](https://developer.apple.com/documentation/xcode/notarizing_your_app_before_distribution/customizing_the_notarization_workflow#3087734) for more information.
 * **staple**: a boolean to specify wether or not the notarization ticket should be stapled to the notarized binary at the end of the process. Default is false. We advise you always set it to `true`. This ensures that Gatekeeper can find the notarization ticket even when a network connection isn’t available.

Once the upload to the notarization is complete, you will receive (the `$RESPONSE` variable in the script above) a JSON file with a content similar to 

```json
{ 
  "uuid":"e68713e3-2ee7-4ebd-8672-20949a9ecdb9",
  "notarizationStatus": {
    "status":"IN_PROGRESS",
    "message":"Uploading' file to Apple notarization 'service"
  }
}
```

The `uuid` field is very important as it will be the one that will let you poll the service to know the status of the notarization process for your file and to download the results in the end. The `notarizationStatus` object contains the current status.

The `$STATUS` will change from `ÌN_PROGRESS` to either `COMPLETE` or `ERROR` depending on the outcome of the process. Here the script polls the service every minute to check if the process is done via the second endpoint `macos-notarization-service/$UUID/status`. 

Once the process is done, you can download the notarized DMG with the endpoint `macos-notarization-service/${UUID}/download`. Note that this is unnecessary if you did not asked for the notarization ticket to be stapled to the binary to be notarized. Indeed, the notarization itself is side effect free for binaries if you don't staple the ticket. 

## Trademarks

* Eclipse® is a Trademark of the Eclipse Foundation, Inc.
* Eclipse Foundation is a Trademark of the Eclipse Foundation, Inc.

## Copyright and license

Copyright 2019 the [Eclipse Foundation, Inc.](https://www.eclipse.org) and [others](https://github.com/eclipse-cbi/macos-notarization-service/graphs/contributors). Code released under the [Eclipse Public License Version 2.0 (EPL-2.0)](https://github.com/eclipse-cbi/macos-notarization-service/blob/src/LICENSE). 

SPDX-License-Identifier: EPL-2.0
