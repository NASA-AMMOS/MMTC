#!/bin/bash

# two args provided to this script:
# - project version string
# - "cli" or "webapp"

set -e

if [ "${2}" = "webapp" ]; then
  DIST_DIR=build/mmtc-webapp-dist-tmp
else
  DIST_DIR=build/mmtc-dist-tmp
fi

if [[ -d $DIST_DIR ]]; then
  rm -r $DIST_DIR
fi

mkdir -p $DIST_DIR

mkdir $DIST_DIR/bin
cp mmtc-core/bin/mmtc                                                             $DIST_DIR/bin

if [[ "$OSTYPE" == "darwin"* ]]; then
  # BSD sed
  sed -i '' "s|@VERSION@|${1}|"                                                   $DIST_DIR/bin/mmtc
else
  # assume Linux (and GNU sed)
  sed -i "s/@VERSION@/${1}/"                                                      $DIST_DIR/bin/mmtc
fi

mkdir $DIST_DIR/conf
cp mmtc-core/src/main/resources/log4j2.xml                                        $DIST_DIR/conf/
cp mmtc-core/src/main/resources/properties.dtd                                    $DIST_DIR/conf/
cp mmtc-core/src/main/resources/TimeCorrelationConfigProperties.xsd               $DIST_DIR/conf/

mkdir $DIST_DIR/conf/examples
cp mmtc-core/src/main/resources/TimeCorrelationConfigProperties-base.xml          $DIST_DIR/conf/examples/
cp mmtc-core/src/test/resources/examples/TimeCorrelationConfigProperties-all.xml  $DIST_DIR/conf/examples/
cp mmtc-core/src/test/resources/examples/SclkPartitionMap.csv                     $DIST_DIR/conf/examples/
cp mmtc-core/src/test/resources/examples/GroundStationMap.csv                     $DIST_DIR/conf/examples/

mkdir $DIST_DIR/lib
cp mmtc-core/build/libs/mmtc-core-$1-app.jar                                      $DIST_DIR/lib/

mkdir -p $DIST_DIR/lib/naif/JNISpice
cp -r jnispice/JNISpice/lib                                                       $DIST_DIR/lib/naif/JNISpice/
cp jnispice/JNISpice/N0067                                                        $DIST_DIR/lib/naif/JNISpice/
cp -r jnispice/JNISpice/doc                                                       $DIST_DIR/lib/naif/JNISpice/

mkdir $DIST_DIR/lib/plugins
cp mmtc-plugin-ampcs/build/libs/mmtc-plugin-ampcs-$1.jar                          $DIST_DIR/lib/plugins/

mkdir $DIST_DIR/docs
cp build/docs/MMTC_Users_Guide.pdf                                                $DIST_DIR/docs

mkdir $DIST_DIR/log/
touch $DIST_DIR/log/.keep

mkdir $DIST_DIR/output/
touch $DIST_DIR/output/.keep

if [ "${2}" = "webapp" ]; then
  cp mmtc-webapp/bin/mmtc-webapp                                                  $DIST_DIR/bin

  if [[ "$OSTYPE" == "darwin"* ]]; then
    # BSD sed
    sed -i '' "s|@VERSION@|${1}|"                                                 $DIST_DIR/bin/mmtc-webapp
  else
    # assume Linux (and GNU sed)
    sed -i "s/@VERSION@/${1}/"                                                    $DIST_DIR/bin/mmtc-webapp
  fi

  cp mmtc-webapp/build/libs/mmtc-webapp-$1.jar                                      $DIST_DIR/lib/
fi
