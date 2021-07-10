#!/bin/bash -e

LAUNCHER="../../launcher-implementation/target/proguard/launcher-implementation-1.2.0-SNAPSHOT-shading.jar"

pushd ci-test/app0
COURSIER_CACHE=/tmp/cache/ java -jar $LAUNCHER @sbt.1.3.13.boot.properties exit
popd

pushd ci-test/app1
COURSIER_CACHE=/tmp/cache/ java -jar $LAUNCHER @sbt.0.13.18.boot.properties exit
popd

pushd ci-test/app2
COURSIER_CACHE=/tmp/cache/ java -jar $LAUNCHER @sbt.1.4.0.boot.properties exit
popd
