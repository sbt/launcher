#!/bin/bash -e
pushd ci-test/app0
COURSIER_CACHE=/tmp/cache/ java -jar ../../launcher-implementation/target/proguard/launcher-implementation-1.2.0-SNAPSHOT.jar @sbt.1.3.13.boot.properties exit
popd

#!/bin/bash -e
pushd ci-test/app1
COURSIER_CACHE=/tmp/cache/ java -jar ../../launcher-implementation/target/proguard/launcher-implementation-1.2.0-SNAPSHOT.jar @sbt.0.13.18.boot.properties exit
popd
