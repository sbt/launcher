import sbt._
import Keys._
import StringUtilities.normalize


object Deps {
  def lib(m: ModuleID) = libraryDependencies += m
  lazy val sbtIo = Def.setting { "org.scala-sbt" % "io" % sbtVersion.value }
  lazy val sbtCompileInterface = Def.setting("org.scala-sbt" % "interface" % sbtVersion.value)
  lazy val scalacheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  lazy val junit = "junit" % "junit" % "4.11"


  lazy val jlineDep = "jline" % "jline" % "2.11"
  lazy val jline = lib(jlineDep)
  lazy val ivy = lib("org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-fccfbd44c9f64523b61398a0155784dcbaeae28f")
  lazy val httpclient = lib("commons-httpclient" % "commons-httpclient" % "3.1")
  lazy val jsch = lib("com.jcraft" % "jsch" % "0.1.46" intransitive ())
  lazy val sbinary = libraryDependencies += "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  lazy val json4sNative = lib("org.json4s" %% "json4s-native" % "3.2.10")
  lazy val jawnParser = lib("org.spire-math" %% "jawn-parser" % "0.6.0")
  lazy val jawnJson4s = lib("org.spire-math" %% "json4s-support" % "0.6.0")
  lazy val scalaCompiler = libraryDependencies <+= scalaVersion(sv => "org.scala-lang" % "scala-compiler" % sv)
  lazy val testInterface = lib("org.scala-sbt" % "test-interface" % "1.0")
  private def scala211Module(name: String, moduleVersion: String) =
    libraryDependencies <++= (scalaVersion)(scalaVersion =>
      if (scalaVersion.startsWith("2.11.") || scalaVersion.startsWith("2.12.")) ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      else Nil
    )
  lazy val scalaXml = scala211Module("scala-xml", "1.0.1")
  lazy val scalaParsers = scala211Module("scala-parser-combinators", "1.0.1")
}
