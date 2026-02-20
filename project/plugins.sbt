addSbtPlugin("com.typesafe.sbt" % "sbt-javaversioncheck" % "0.1.0")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.5")
addSbtPlugin("com.github.sbt" % "sbt-proguard" % "0.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-nocomma" % "0.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.1.5")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

scalacOptions += "-feature"
