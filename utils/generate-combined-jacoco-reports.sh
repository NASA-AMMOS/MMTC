#!/bin/bash

# Finds and merges all .exec files within this directory into a single report.
# Must be run from its own directory.  Takes no arguments.

set -ex

cd ..

JACOCO_CLI=`find mmtc-end-to-end-tests/src/test/resources/test-utils -name "org.jacoco.cli-*.jar"`

# set up directories

MERGE_DIR=${PWD}/build/jacoco/merge
OUT_DIR=${PWD}/build/jacoco/out

if [ -d $OUT_DIR ]; then
  rm -r $OUT_DIR
fi

if [ -d $MERGE_DIR ]; then
  rm -r $MERGE_DIR
fi

mkdir -p $MERGE_DIR
mkdir -p $OUT_DIR

# copy all found *.exec files into $MERGE_DIR
find . -name "*.exec" -exec sh -c 'name='$MERGE_DIR'/$(uuidgen).exec_cp; cp "$1" "$name"' sh {} \;

# use Jacoco CLI to merge all individual *.exec files into a single .exec file
find $MERGE_DIR -name "*.exec_cp" -print0 | xargs -0 -s 10000 java -jar $JACOCO_CLI merge --destfile $OUT_DIR/merged.exec

java -jar $JACOCO_CLI \
report $OUT_DIR/merged.exec \
--classfiles=mmtc-core/build/classes/java/main \
--classfiles=mmtc-plugin-ampcs/build/classes/java/main \
--classfiles=mmtc-webapp-ampcs/build/classes/java/main \
--html $OUT_DIR/jacoco-out
