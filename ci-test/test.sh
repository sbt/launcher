#!/bin/bash -ex

declare -a LAUNCHERS=(
"../../launcher/target/launcher-1.7.0-SNAPSHOT.jar"
)
for LAUNCHER in "${LAUNCHERS[@]}"
do
  pushd ci-test/app0
  find . -name target -delete || true
  COURSIER_CACHE=/tmp/cache/ java -jar $LAUNCHER @sbt.1.3.13.boot.properties exit
  popd

  pushd ci-test/app1
  find . -name target -delete || true
  COURSIER_CACHE=/tmp/cache/ java -jar $LAUNCHER @sbt.0.13.18.boot.properties exit
  popd

  pushd ci-test/app2
  find . -name target -delete || true
  COURSIER_CACHE=/tmp/cache/ java -jar $LAUNCHER @sbt.1.4.0.boot.properties exit
  popd
done
