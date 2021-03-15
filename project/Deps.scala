import sbt._
import sbt.Keys._

object Deps {
  def lib(m: ModuleID) = libraryDependencies += m
  lazy val sbtIo = "org.scala-sbt" %% "io" % "1.4.0"
  lazy val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.3"
  lazy val junit = "junit" % "junit" % "4.11"
  lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

  // TODO - these should be like the above, just ModuleIDs
  lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-839fad1cdc07cf6fc81364d74c323867230432ad"
  lazy val coursier = "io.get-coursier" %% "coursier" % "2.0.13"
  lazy val scalaCompiler = Def.setting("org.scala-lang" % "scala-compiler" % scalaVersion.value)
}
