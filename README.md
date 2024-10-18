# macOS Notarization Service

Dummy change

[![Build Status](https://ci.eclipse.org/cbi/buildStatus/icon?job=macos-notarization-service%2Fmain)](https://ci.eclipse.org/cbi/job/macos-notarization-service/job/main/) 
[![GitHub release](https://img.shields.io/github/release/eclipse-cbi/macos-notarization-service.svg?label=release)](https://github.com/eclipse-cbi/macos-notarization-service.svg/releases/latest)
[![GitHub license](https://img.shields.io/github/license/eclipse-cbi/macos-notarization-service.svg)](https://github.com/eclipse-cbi/macos-notarization-service/blob/master/LICENSE)
[![SLSA Build Level](https://img.shields.io/badge/SLSA%20Build-Level%203-freen?style=flat&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIj8+CjxzdmcgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB3aWR0aD0iMTAxIiBoZWlnaHQ9IjI4IiB2aWV3Qm94PSIwIDAgMjggMjgiIGZpbGw9Im5vbmUiPgo8ZyBjbGlwLXBhdGg9InVybCgjY2xpcDBfMTIzXzExMjcpIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0yNi4xMDUyIDMuMDYzNjhlLTA1TDI2LjE4MjIgLTAuMDg3MDU1N0wyNC42ODM2IC0xLjQxMTVMMjQuMDIxNCAtMC42NjIxOTFDMjMuODIzNyAtMC40Mzg1MDYgMjMuNjIzMiAtMC4yMTc3NTIgMjMuNDE5OSAzLjA2MzY4ZS0wNUgyLjg2MTAyZS0wNlYxLjU1ODQxTC0xLjM3NTk4IDIuNDA2NDZMLTAuODUxMjk3IDMuMjU3NzZDLTAuNTc2NjAyIDMuNzAzNDYgLTAuMjkyNzI3IDQuMTQxNjggMi44NjEwMmUtMDYgNC41NzIyNFYyMy4yNTY0Qy0wLjAwNjc4MTgyIDIzLjI1NjYgLTAuMDEzNTY2OSAyMy4yNTY3IC0wLjAyMDM1MjEgMjMuMjU2OUwtMS4wMjAxNCAyMy4yNzcyTC0wLjk3OTQzNSAyNS4yNzY4TDIuODYxMDJlLTA2IDI1LjI1NjlWMjhIMjhWMTAuMjYzMkMyOC4yODgxIDkuODQ4NTUgMjguNTY3OSA5LjQyNjYyIDI4LjgzOTIgOC45OTc1OUMyOS4yOTQ5IDguMzExNzMgMjkuNjMzMyA3LjcyNTMyIDI5Ljg1OTggNy4zMDZDMjkuOTczNyA3LjA5NTIxIDMwLjA1OTUgNi45MjYyOSAzMC4xMTc4IDYuODA3NzZDMzAuMTQ3IDYuNzQ4NDkgMzAuMTY5MyA2LjcwMTc4IDMwLjE4NDkgNi42Njg3TDMwLjIwMzIgNi42Mjk0MkwzMC4yMDg2IDYuNjE3NjlMMzAuMjEwNCA2LjYxMzg0TDMwLjIxMSA2LjYxMjQzTDMwLjIxMTMgNi42MTE4NUwzMC4yMTE0IDYuNjExNTlDMzAuMjExNCA2LjYxMTQ4IDMwLjIxMTUgNi42MTEzNiAyOS4zNTU3IDYuMjI1MTVMMzAuMjExNSA2LjYxMTM2TDMwLjYyMjggNS42OTk4N0wyOC43OTk4IDQuODc3MjJMMjguMzg5IDUuNzg3NThMMjguMzg4OSA1Ljc4Nzc5TDI4LjM4ODggNS43ODc5N0wyOC4zODg3IDUuNzg4M0wyOC4zODg2IDUuNzg4NDlMMjguMzg4NSA1Ljc4ODdMMjguMzg2OSA1Ljc5MjE5TDI4LjM3NTYgNS44MTY1MUMyOC4zNjQ3IDUuODM5NSAyOC4zNDc0IDUuODc1ODMgMjguMzIzNCA1LjkyNDQ4QzI4LjI3NTUgNi4wMjE4IDI4LjIwMTMgNi4xNjgyNCAyOC4xMDAyIDYuMzU1NDhDMjguMDY5MyA2LjQxMjY3IDI4LjAzNTkgNi40NzM2MiAyOCA2LjUzODExVjMuMDYzNjhlLTA1SDI2LjEwNTJaTTI2LjEwNTIgMy4wNjM2OGUtMDVIMjMuNDE5OUMxOS4wMTA2IDQuNzIzNyAxMy4zMDE1IDguMDQ5MjcgNy4wMjE1MyA5LjU1NThDNC42NDY1NyA3LjQ0NzY3IDIuNTYwNTYgNC45ODE2OSAwLjg1MTMwMyAyLjIwODRMMC4zMjY2MjMgMS4zNTcxTDIuODYxMDJlLTA2IDEuNTU4NDFWNC41NzIyNEMxLjc0MTUzIDcuMTMzNzMgMy43OTY0OCA5LjQyNCA2LjA5NjQ3IDExLjQwMzVDOS40MDI0NCAxNC4yNDg3IDEzLjIxNDcgMTYuNDUxOCAxNy4zMzA0IDE3Ljg5NTZDMTMuODQ1NyAyMC4xNzMxIDkuOTM3OTUgMjEuNzg1MyA1LjgwODAxIDIyLjYxNjZDMy45MTI1MiAyMi45OTgxIDEuOTcwMDggMjMuMjE1MSAyLjg2MTAyZS0wNiAyMy4yNTY0VjI1LjI1NjlMMC4wMjAzNTc4IDI1LjI1NjRDMi4xMTcxNyAyNS4yMTM4IDQuMTg0ODkgMjQuOTgzNCA2LjIwMjY0IDI0LjU3NzJDMTEuMjgzMiAyMy41NTQ2IDE2LjA0NjEgMjEuNDE4MSAyMC4xNTk3IDE4LjM1OTNDMjMuMTYxOCAxNi4xMjcxIDI1LjgxODMgMTMuNDAzNSAyOCAxMC4yNjMyVjYuNTM4MTFDMjcuODAwNyA2Ljg5NjI2IDI3LjUyNDEgNy4zNjM1MSAyNy4xNjc0IDcuODk5NzFMMjcuMTYwOSA3LjkwOTUyTDI3LjE1NDYgNy45MTk0OEMyNS4wNTIyIDExLjI0NzMgMjIuNDAyOCAxNC4xMjI2IDE5LjM2MjEgMTYuNDU1NEMxNS41ODU5IDE1LjMxMzYgMTIuMDUxMyAxMy41MDM4IDguOTI2MjggMTEuMTIzQzE1LjMyMjcgOS4yOTcxOSAyMS4wOTE1IDUuNjczMDkgMjUuNTIgMC42NjIyNTJMMjYuMTA1MiAzLjA2MzY4ZS0wNVoiIGZpbGw9IiNGRjY3NDAiLz4KPC9nPgo8ZGVmcz4KPGNsaXBQYXRoIGlkPSJjbGlwMF8xMjNfMTEyNyI+CjxwYXRoIGQ9Ik0wIDUuNkMwIDIuNTA3MjEgMi41MDcyMSAwIDUuNiAwSDIyLjRDMjUuNDkyOCAwIDI4IDIuNTA3MjEgMjggNS42VjIyLjRDMjggMjUuNDkyOCAyNS40OTI4IDI4IDIyLjQgMjhINS42QzIuNTA3MjEgMjggMCAyNS40OTI4IDAgMjIuNFY1LjZaIiBmaWxsPSJ3aGl0ZSIvPgo8L2NsaXBQYXRoPgo8L2RlZnM+Cjwvc3ZnPgo=)](https://slsa.dev/spec/v1.0/levels#build-l3)

This is a web service that runs on macOS and offers a REST API to notarize signed application bundles or signed DMG. You can read more about notarization on the [Apple developer website](https://developer.apple.com/documentation/security/notarizing_your_app_before_distribution).

## Getting started

### Requirements

* Java 17+. We advise to use the [Temurin](https://adoptium.net/) binaries via [homebrew](https://brew.sh)

```bash
brew install temurin17
```

### Build

```bash
$ ./mvnw clean package
```

It produces the self-contained application in `/target/quarkus-app`.

You can run the application using: `java -jar target/quarkus-app/quarkus-run.jar`

See below for advanced startup method.

### Installation

To download a release and perform verification whether the downloaded artifact has been produced by the project,
you should use the `download-github-release.sh` script (supported since `v1.2.0`):

```bash
$ ./download-github-release.sh -v 1.3.0
```

This will download the `1.3.0` release together with the provenance and perform verification (requires that the [slsa-verifier](https://github.com/slsa-framework/slsa-verifier) tool is installe):

```bash
$ ./download-github-release.sh -v 1.3.0
REPO = eclipse-cbi/macos-notarization-service
VERSION = 1.3.0
ARTIFACT = macos-notarization-service
Downloaded artifact 'macos-notarization-service-1.3.0.zip'
Downloaded provenance 'macos-notarization-service-1.3.0-attestation.intoto.build.slsa'
Verifying artifact 'macos-notarization-service-1.3.0.zip' using provenance 'macos-notarization-service-1.3.0-attestation.intoto.build.slsa':

Verified build using builder "https://github.com/jreleaser/release-action/.github/workflows/builder_slsa3.yml@refs/tags/v1.1.0-java" at commit 5325c11c611568f5e043d934185183783f228c0a
Verifying artifact macos-notarization-service-1.3.0.zip: PASSED

PASSED: Verified SLSA provenance
```


### Run

Configure `NOTARIZATION_APPLEID_USERNAME`, `NOTARIZATION_APPLEID_PASSWORD` and `NOTARIZATION_APPLEID_TEAMID` with the proper values for your Apple developer ID account in file `start.sh`. Then, just run

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
