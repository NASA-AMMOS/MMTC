#!/bin/bash

# This script recreates the 'JNISpice' directory in the :jnispice subproject.
# This script only needs to be run when we need to update MMTC's JNISpice dependency.
# Adjust the URL below and run the script, then run a clean Gradle build/other tasks as necessary.

set -ex

rm -rf JNISpice
rm -rf JNISpice-extract

wget https://naif.jpl.nasa.gov/pub/naif/misc/JNISpice/PC_Linux_GCC_Java1.8_64bit/packages/JNISpice.tar.Z
tar -zxf JNISpice.tar.Z
rm JNISpice.tar.Z
mv JNISpice JNISpice-extract

mkdir JNISpice
cp JNISpice-extract/N00*                                   JNISpice/

mkdir JNISpice/doc
cp JNISpice-extract/doc/dscriptn.txt                       JNISpice/doc
cp JNISpice-extract/doc/version.txt                        JNISpice/doc
cp JNISpice-extract/doc/whats.new                          JNISpice/doc

cp -r JNISpice-extract/lib                                 JNISpice/

mkdir JNISpice/classes
cp -r JNISpice-extract/src/JNISpice/spice/basic/*.class    JNISpice/classes

rm -r JNISpice-extract
