import Deps._
import Util._

// the launcher is published with metadata so that the scripted plugin can pull it in
// being proguarded, it shouldn't ever be on a classpath with other jars, however
def proguardedLauncherSettings = Seq(
  publishArtifact in packageSrc := false,
  moduleName := "sbt-launch",
  autoScalaLibrary := false,
  description := "sbt application launcher"
)

def launchSettings =
  Seq(ivy,
    compile in Test <<= compile in Test dependsOn (publishLocal in testSamples, publishLocal in launchInterfaceSub)
  ) ++
    inConfig(Compile)(Transform.configSettings) ++
    inConfig(Compile)(Transform.transSourceSettings ++ Seq(
      // TODO - these should be shared between sbt core + sbt-launcher...
      Transform.inputSourceDirectory <<= sourceDirectory / "input_sources",
      Transform.sourceProperties := Map("cross.package0" -> "xsbt", "cross.package1" -> "boot")
    ))

lazy val launchInterfaceSub =
  minProject(file("interface"), "Launcher Interface") settings (javaOnly: _*) settings(
    resourceGenerators in Compile <+= (version, resourceManaged, streams, compile in Compile) map generateVersionFile("sbt.launcher.version.properties")
  )

// the launcher.  Retrieves, loads, and runs applications based on a configuration file.
lazy val launchSub = testedBaseProject(file("."), "Launcher") dependsOn (
  launchInterfaceSub
) settings (launchSettings: _*) settings(
  libraryDependencies ++= Seq(
    sbtIo.value % "test->test",
    sbtCompileInterface.value % "test"
  )
)

// used to test the retrieving and loading of an application: sample app is packaged and published to the local repository
lazy val testSamples = noPublish(baseProject(file("test-sample"), "Launch Test")) dependsOn (launchInterfaceSub) settings (scalaCompiler) settings(
  libraryDependencies += "org.scala-sbt" % "interface" % sbtVersion.value
)

def sbtBuildSettings = Seq(
  version := "0.13.8-SNAPSHOT",
  publishArtifact in packageDoc := false,
  scalaVersion := "2.10.4",
  publishMavenStyle := false,
  crossPaths := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true)
)

Project.inScope(Scope.GlobalScope in ThisBuild)(sbtBuildSettings)
LaunchProguard.settings
LaunchProguard.specific(launchSub)

configs(LaunchProguard.Proguard)


