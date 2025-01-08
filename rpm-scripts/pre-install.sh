#!/bin/bash

set -ex

# create the mmtc user and group if they don't exist
getent passwd mmtc >/dev/null 2>&1 || useradd --system mmtc
