#!/bin/bash

set -ex

CONFIG_KEY=$1
CONFIG_VAL=$2
CONFIG_PATH=$3

FIND="^\s*<entry key=\"${CONFIG_KEY}\">.*</entry>$"
REPLACE="  <entry key=\"${CONFIG_KEY}\">${CONFIG_VAL}</entry>";

sed -E -i "s|${FIND}|${REPLACE}|" "${CONFIG_PATH}"
