#!/bin/bash

set -e

SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
echo "Executing for script dir $SCRIPT_DIR"
# Set env variables
export JAVA_HOME="/lib/jvm/java-1.8.0/"
export PATH=$PATH:$JAVA_HOME/bin
export TK_CONFIG_PATH="$SCRIPT_DIR/conf"
echo "Environment vars set"

# Adjust necessary config keys
source ./update-config-key.sh telemetry.source.plugin.rawTlmTable.tableFile.uri "FILE://$SCRIPT_DIR/RawTelemetryTable_NH_reformatted.csv" "$SCRIPT_DIR/conf/TimeCorrelationConfigProperties.xml"

$SCRIPT_DIR/bin/mmtc 2017-342T00:00:00 2017-342T23:59:59 -F --clkchgrate-compute p
$SCRIPT_DIR/bin/mmtc 2017-343T00:00:00 2017-343T23:59:59 -F --clkchgrate-compute i
$SCRIPT_DIR/bin/mmtc 2017-344T00:00:00 2017-344T23:59:59 -F --clkchgrate-compute i --generate-cmd-file
$SCRIPT_DIR/bin/mmtc 2017-345T00:00:00 2017-345T23:59:59 -F --clkchgrate-compute i