#!/bin/bash

# only arg provided to this script must be project version string

set -e

DEMO_DIR=build/mmtc-demo-tmp

if [[ -d $DEMO_DIR ]]; then
  rm -r                                                                             $DEMO_DIR
fi

mkdir -p                                                                            $DEMO_DIR

cp mmtc-core/src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv         $DEMO_DIR
cp utils/update-config-key.sh                                                       $DEMO_DIR
cp utils/setup-and-run-demo.sh                                                      $DEMO_DIR

mkdir $DEMO_DIR/bin
cp mmtc-core/bin/mmtc                                                               $DEMO_DIR/bin

if [[ "$OSTYPE" == "darwin"* ]]; then
  # BSD sed
  sed -i '' "s|@VERSION@|${1}|"                                                     $DEMO_DIR/bin/mmtc
else
  # assume Linux (and GNU sed)
  sed -i "s/@VERSION@/${1}/"                                                        $DEMO_DIR/bin/mmtc
fi

mkdir $DEMO_DIR/conf
cp mmtc-core/src/main/resources/log4j2.xml                                          $DEMO_DIR/conf/
cp mmtc-core/src/main/resources/properties.dtd                                      $DEMO_DIR/conf/

cp mmtc-core/src/main/resources/TimeCorrelationConfigProperties.xsd                 $DEMO_DIR/conf/
cp mmtc-core/src/test/resources/demo/TimeCorrelationConfigProperties.xml            $DEMO_DIR/conf/

cp mmtc-core/src/test/resources/SclkPartitionMap.csv                                $DEMO_DIR/conf/
cp mmtc-core/src/test/resources/GroundStationMap.csv                                $DEMO_DIR/conf/

mkdir $DEMO_DIR/conf/examples
cp mmtc-core/src/main/resources/TimeCorrelationConfigProperties-base.xml            $DEMO_DIR/conf/examples/
cp mmtc-core/src/test/resources/examples/TimeCorrelationConfigProperties-all.xml    $DEMO_DIR/conf/examples/
cp mmtc-core/src/test/resources/examples/SclkPartitionMap.csv                       $DEMO_DIR/conf/examples/
cp mmtc-core/src/test/resources/examples/GroundStationMap.csv                       $DEMO_DIR/conf/examples/

mkdir $DEMO_DIR/lib
cp mmtc-core/build/libs/mmtc-core-$1-app.jar                                        $DEMO_DIR/lib/

mkdir -p $DEMO_DIR/lib/naif/JNISpice
cp -r jnispice/JNISpice/lib                                                         $DEMO_DIR/lib/naif/JNISpice/
cp jnispice/JNISpice/N0067                                                          $DEMO_DIR/lib/naif/JNISpice/
cp -r jnispice/JNISpice/doc                                                         $DEMO_DIR/lib/naif/JNISpice/

mkdir $DEMO_DIR/lib/plugins
cp mmtc-plugin-ampcs/build/libs/mmtc-plugin-ampcs-$1.jar                            $DEMO_DIR/lib/plugins/

mkdir $DEMO_DIR/docs
cp build/docs/MMTC_Users_Guide.pdf                                                  $DEMO_DIR/docs

mkdir $DEMO_DIR/log/
touch $DEMO_DIR/log/.keep

mkdir $DEMO_DIR/output/
mkdir $DEMO_DIR/output/sclk/
cp mmtc-core/src/test/resources/nh_kernels/sclk/new-horizons_1000.tsc               $DEMO_DIR/output/sclk/
touch $DEMO_DIR/output/.keep

cp -r mmtc-core/src/test/resources/nh_kernels                                       $DEMO_DIR/kernels

cd $DEMO_DIR
zip -r ./mmtc-$1-demo.zip                         .
cp mmtc-$1-demo.zip ../distributions