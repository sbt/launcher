libraryDependencies ++= Seq(
  "net.databinder" %% "dispatch-http" % "0.8.9",
  "org.jsoup" % "jsoup" % "1.7.1"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.6.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-javaversioncheck" % "0.1.0")
