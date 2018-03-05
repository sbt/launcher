import Deps._
import Util._
import com.typesafe.tools.mima.core._, ProblemFilters._

// the launcher is published with metadata so that the scripted plugin can pull it in
// being proguarded, it shouldn't ever be on a classpath with other jars, however
def proguardedLauncherSettings = Seq(
  publishArtifact in packageSrc := false,
  moduleName := "sbt-launch",
  autoScalaLibrary := false,
  description := "sbt application launcher"
)

def launchSettings =
    inConfig(Compile)(Transform.configSettings) ++
    inConfig(Compile)(Transform.transSourceSettings ++ Seq(
      // TODO - these should be shared between sbt core + sbt-launcher...
      Transform.inputSourceDirectory := sourceDirectory.value / "input_sources",
      Transform.sourceProperties := Map("cross.package0" -> "xsbt", "cross.package1" -> "boot")
    ))

// The interface JAR for projects which want to be launched by sbt.
lazy val launchInterfaceSub =
  minProject(file("launcher-interface"), "Launcher Interface").settings(javaOnly).settings(
    resourceGenerators in Compile += Def.task{
      generateVersionFile("sbt.launcher.version.properties")(version.value, resourceManaged.value, streams.value, (compile in Compile).value)
    }.taskValue,
    description := "Interfaces for launching projects with the sbt launcher",
    mimaPreviousArtifacts := Set(organization.value % moduleName.value % "1.0.1")
  ).settings(Release.settings)

// the launcher.  Retrieves, loads, and runs applications based on a configuration file.
// TODO - move into a directory called "launcher-impl or something."
lazy val launchSub = noPublish(baseProject(file("launcher-implementation"), "Launcher Implementation")).
  dependsOn(launchInterfaceSub).
  settings(launchSettings).
  settings(
    libraryDependencies ++= Seq(
      ivy,
      sbtIo.value % "test->test",
      sbtCompileInterface.value % "test",
      Deps.scalacheck % "test",
      Deps.specs2 % "test",
      Deps.junit % "test"
    ),
    compile in Test := {
      val ignore = (publishLocal in testSamples).value
      val ignore2 = (publishLocal in launchInterfaceSub).value
      (compile in Test).value
    }
    // TODO: Configure MiMa, deal with Proguard
  )

// used to test the retrieving and loading of an application: sample app is packaged and published to the local repository
lazy val testSamples = noPublish(baseProject(file("test-sample"), "Launch Test")) dependsOn (launchInterfaceSub) settings(
  libraryDependencies ++=
    Seq(
      sbtCompileInterface.value,
      scalaCompiler.value
    )
)

def sbtBuildSettings = Seq(
  bintrayPackage := "launcher",
  version := "1.0.4-SNAPSHOT",
  publishArtifact in packageDoc := true,
  scalaVersion := "2.10.7",
  publishMavenStyle := true,
  crossPaths := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true)
)

// Configuration for the launcher root project (the proguarded launcher)
Project.inScope(Scope.GlobalScope in ThisBuild)(sbtBuildSettings)
LaunchProguard.settings
LaunchProguard.specific(launchSub)
javaOnly
packageBin in Compile := (LaunchProguard.proguard in LaunchProguard.Proguard).value
packageSrc in Compile := (packageSrc in Compile in launchSub).value
packageDoc in Compile := (packageDoc in Compile in launchSub).value
Util.commonSettings("launcher")
Release.settings
description := "Standalone launcher for maven/ivy deployed projects."
configs(LaunchProguard.Proguard)

commands += Command.command("release") { state =>
  "clean" ::
  "test" ::
  "publishSigned" ::
  state
}
