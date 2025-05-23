#!/bin/bash

# Generate .well-known/assetlinks.json file (on standard output).
# This script must be run on the top level of the repo.

resourceFolder=multipaz-backend-server/src/main/resources/resources/

function readList() {
  sed 's/#.*$//' < "$resourceFolder$1"
}

function item() {
cat << eof
{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "$1",
    "sha256_cert_fingerprints": [
	   "$2"
    ]
  }
}
eof
}

separator=""
echo -n "["
for package in $(readList android_trusted_package_names.txt)
do
  for hash in $(readList android_trusted_app_signatures.txt)
  do
     echo $separator
     separator=","
     item "$package" "$hash"
  done
done
echo "]"