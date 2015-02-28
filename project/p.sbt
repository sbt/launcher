libraryDependencies ++= Seq(
  "net.databinder" %% "dispatch-http" % "0.8.9",
  "org.jsoup" % "jsoup" % "1.7.1"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-javaversioncheck" % "0.1.0")

addSbtPlugin("org.scala-sbt" % "sbt-houserules"  % "0.1.0")
