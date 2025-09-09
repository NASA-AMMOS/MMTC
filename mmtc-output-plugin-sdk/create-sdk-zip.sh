#!/bin/bash

# only arg provided to this script must be project version string

set -e

SDK_DIR=build/mmtc-sdk-tmp

if [[ -d $SDK_DIR ]]; then
  rm -r $SDK_DIR
fi

mkdir -p $SDK_DIR

cp etc/*                                                            $SDK_DIR/

if [[ "$OSTYPE" == "darwin"* ]]; then
  # BSD sed
  sed -i '' "s|@VERSION@|${1}|"                                     $SDK_DIR/build.gradle.kts
else
  # assume Linux (and GNU sed)
  sed -i "s/@VERSION@/${1}/"                                        $SDK_DIR/build.gradle.kts
fi

cp -r src                                                           $SDK_DIR/

mkdir -p $SDK_DIR/lib/edu/jhuapl/sd/sig/mmtc-core/$1/
cp ../mmtc-core/build/publications/mmtc-core/pom-default.xml        $SDK_DIR/lib/edu/jhuapl/sd/sig/mmtc-core/$1/mmtc-core-$1.pom
cp ../mmtc-core/build/libs/mmtc-core-$1.jar                         $SDK_DIR/lib/edu/jhuapl/sd/sig/mmtc-core/$1/
cp ../mmtc-core/build/libs/mmtc-core-$1-javadoc.jar                 $SDK_DIR/lib/edu/jhuapl/sd/sig/mmtc-core/$1/
cp ../mmtc-core/build/libs/mmtc-core-$1-sources.jar                 $SDK_DIR/lib/edu/jhuapl/sd/sig/mmtc-core/$1/

mkdir $SDK_DIR/docs
if [[ -f ../build/docs/MMTC_Users_Guide.pdf ]]; then
  cp ../build/docs/MMTC_Users_Guide.pdf                               $SDK_DIR/docs
else
  cp ../../build/docs/MMTC_Users_Guide.pdf                            $SDK_DIR/docs
fi

cp -r ../gradlew                                                    $SDK_DIR
cp -r ../gradle                                                     $SDK_DIR

cd $SDK_DIR
zip -qr ../mmtc-$1-output-plugin-sdk.zip                         .
