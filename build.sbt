import Deps._
import Util._
import com.typesafe.tools.mima.core._, ProblemFilters._

ThisBuild / version     := "1.1.7-SNAPSHOT"
ThisBuild / description := "Standalone launcher for maven/ivy deployed projects"
ThisBuild / bintrayPackage := "launcher"
ThisBuild / scalaVersion := "2.10.7"
ThisBuild / publishMavenStyle := true
ThisBuild / crossPaths := false
ThisBuild / resolvers += Resolver.typesafeIvyRepo("releases")
ThisBuild / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1")

lazy val root = (project in file("."))
  .configs(LaunchProguard.Proguard)
  .settings(LaunchProguard.settings ++ LaunchProguard.specific(launchSub) ++ javaOnly ++ Util.commonSettings("launcher") ++ Release.settings)
  .settings(nocomma {
    packageBin in Compile := (LaunchProguard.proguard in LaunchProguard.Proguard).value
    packageSrc in Compile := (packageSrc in Compile in launchSub).value
    packageDoc in Compile := (packageDoc in Compile in launchSub).value
    commands += Command.command("release") { state =>
      "clean" ::
      "test" ::
      "publishSigned" ::
      state
    }
  })

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
lazy val launchInterfaceSub = (project in file("launcher-interface"))
  .settings(javaOnly)
  .settings(nocomma {
    name := "Launcher Interface"
    resourceGenerators in Compile += Def.task{
      generateVersionFile("sbt.launcher.version.properties")(version.value, resourceManaged.value, streams.value, (compile in Compile).value)
    }.taskValue
    description := "Interfaces for launching projects with the sbt launcher"
    mimaPreviousArtifacts := Set(organization.value % moduleName.value % "1.0.1")
    mimaBinaryIssueFilters ++= Seq(
      exclude[ReversedMissingMethodProblem]("xsbti.MavenRepository.allowInsecureProtocol"),
      exclude[ReversedMissingMethodProblem]("xsbti.IvyRepository.allowInsecureProtocol")
    )
    exportJars := true
  })
  .settings(Release.settings)

// the launcher.  Retrieves, loads, and runs applications based on a configuration file.
// TODO - move into a directory called "launcher-impl or something."
lazy val launchSub = (project in file("launcher-implementation"))
  .dependsOn(launchInterfaceSub)
  .settings(Util.base)
  .settings(launchSettings)
  .settings(nocomma {
    name := "Launcher Implementation"
    publish / skip := true
    libraryDependencies ++= Seq(
      ivy,
      sbtIo.value % "test->test",
      sbtCompileInterface.value % "test",
      Deps.scalacheck % "test",
      Deps.specs2 % "test",
      Deps.junit % "test"
    )
    compile in Test := {
      val ignore = (publishLocal in testSamples).value
      val ignore2 = (publishLocal in launchInterfaceSub).value
      (compile in Test).value
    }
    // TODO: Configure MiMa, deal with Proguard
  })

// used to test the retrieving and loading of an application: sample app is packaged and published to the local repository
lazy val testSamples = (project in file("test-sample"))
  .dependsOn(launchInterfaceSub)
  .settings(nocomma {
    name := "Launch Test"
    publish / skip := true
    libraryDependencies ++=
      Seq(
        sbtCompileInterface.value,
        scalaCompiler.value
      )
  })
