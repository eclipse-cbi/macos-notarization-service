#!/usr/bin/env bash
set -euo pipefail

download() {
  local URL=$1
  local OUTPUT_FILE=$2
  local TMP_FILE=$(mktemp)
  local HTTP_CODE=$(curl --silent --output ${TMP_FILE} --write-out "%{http_code}" -L "${URL}")
  if [[ ${HTTP_CODE} -lt 200 || ${HTTP_CODE} -gt 299 ]]; then
    rm ${TMP_FILE}
    return 1
  fi
  cp ${TMP_FILE} ${OUTPUT_FILE}
  rm ${TMP_FILE}
  return 0
}

download-artifact() {
  if ! download ${ARTIFACT_URL} ${ARTIFACT_FILENAME}; then
    echo "Failed to download artifact '${ARTIFACT_FILENAME}'"
    exit 1
  else
    echo "Downloaded artifact '${ARTIFACT_FILENAME}'"
  fi
}

download-provenance() {
  if ! download ${PROVENANCE_URL} ${PROVENANCE_FILENAME}; then
    return 1
  else
    return 0
  fi
}

verify() {
  echo "Verifying artifact '${ARTIFACT_FILENAME}' using provenance '${PROVENANCE_FILENAME}':"
  echo ""

  # disable --source-tag for now as jreleaser uses the main branch as sourceURI for now:
  # --source-tag "v${VERSION}"

  slsa-verifier verify-artifact \
     --provenance-path ${PROVENANCE_FILENAME} \
     --source-uri "github.com/${REPO}" \
     --builder-id "https://github.com/jreleaser/release-action/.github/workflows/builder_slsa3.yml" \
     ${ARTIFACT_FILENAME}
}

usage() {
  local USAGE
  USAGE="
Usage: $(basename "${0}") [OPTIONS]

This scripts downloads the specified release from a GitHub repository and verifies it with the attached SLSA provenance.

Options:
  -a ARTIFACT    the artifact to download, e.g. macos-notarization-service
  -e EXTENSION   the extension to use, default: .zip
  -r REPO        the GitHub repo to use for download, format: owner/repo-name, e.g. eclipse-cbi/macos-notarization-service
  -v VERSION     the release version to download, e.g. 1.3.0
  -f             force downloading artifact and provenance
  -h             show this help

"
  echo "$USAGE"
  exit 1
}


if ! command -v slsa-verifier &> /dev/null
then
    echo "slsa-verifier could not be found, follow instructions at https://github.com/slsa-framework/slsa-verifier"
    exit 1
fi

ARTIFACT="macos-notarization-service"
EXTENSION=".zip"
REPO="eclipse-cbi/macos-notarization-service"
FORCE=false

while getopts ":a:e:r:v:f" o; do
    case "${o}" in
        a)
            ARTIFACT=${OPTARG}
            ;;
        e)
            EXTENSION=${OPTARG}
            ;;
        r)
            REPO=${OPTARG}
            ;;
        v)
            VERSION=${OPTARG}
            ;;
        f)
            FORCE=true
            ;;
        *)
            usage
            ;;
    esac
done

shift $((OPTIND-1))

if [ -z "${REPO-}" ] || [ -z "${VERSION-}" ] || [ -z "${ARTIFACT-}" ]; then
    usage
fi

echo "REPO = ${REPO}"
echo "VERSION = ${VERSION}"
echo "ARTIFACT = ${ARTIFACT}"

ARTIFACT_FILENAME="${ARTIFACT}-${VERSION}${EXTENSION}"
ARTIFACT_URL="https://github.com/${REPO}/releases/download/v${VERSION}/${ARTIFACT_FILENAME}"

if [ -f "$ARTIFACT_FILENAME" ] && [ "$FORCE" == false ]; then
    echo "Using local artifact '${ARTIFACT_FILENAME}'"
else
  download-artifact
fi

FOUND=false
# the attestation filename has changed after using jreleaser
ATTESTATION_SUFFIXES=("-attestation.intoto.build.slsa" ".zip.intoto.jsonl")
for SUFFIX in "${ATTESTATION_SUFFIXES[@]}"
do
  PROVENANCE_FILENAME="${ARTIFACT}-${VERSION}${SUFFIX}"
  PROVENANCE_URL="https://github.com/${REPO}/releases/download/v${VERSION}/${PROVENANCE_FILENAME}"

  if [ -f "${PROVENANCE_FILENAME}" ] && [ "$FORCE" == false ]; then
    echo "Using local provenance '${PROVENANCE_FILENAME}'"
    FOUND=true
    break
  fi

  if download-provenance; then
    echo "Downloaded provenance '${PROVENANCE_FILENAME}'"
    FOUND=true
    break
  fi
done

if [ "${FOUND}" = false ]; then
  echo "Failed to download provenance, aborting"
  exit 1
fi

verify
