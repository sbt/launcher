#!/bin/bash -e
pushd ci-test/app0
java -jar ../../launcher-implementation/target/proguard/launcher-implementation-1.2.0-SNAPSHOT.jar @sbt.1.3.13.boot.properties exit
popd
