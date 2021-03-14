import Deps._
import Util._
import com.typesafe.tools.mima.core._, ProblemFilters._

lazy val keepFullClasses = settingKey[Seq[String]]("Fully qualified names of classes that proguard should preserve the non-private API of.")

ThisBuild / version     := "1.1.7-SNAPSHOT"
ThisBuild / description := "Standalone launcher for maven/ivy deployed projects"
ThisBuild / bintrayPackage := "launcher"
ThisBuild / scalaVersion := "2.12.13"
ThisBuild / publishMavenStyle := true
ThisBuild / crossPaths := false
ThisBuild / resolvers += Resolver.typesafeIvyRepo("releases")
ThisBuild / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1")

lazy val root = (project in file("."))
  .aggregate(launchInterfaceSub, launchSub)
  .settings(javaOnly ++ Util.commonSettings("launcher") ++ Release.settings)
  .settings(nocomma {
    mimaPreviousArtifacts := Set.empty
    // packageBin in Compile := (LaunchProguard.proguard in LaunchProguard.Proguard).value
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
  .enablePlugins(SbtProguard)
  .dependsOn(launchInterfaceSub)
  .settings(Util.base)
  .settings(launchSettings)
  .settings(nocomma {
    name := "Launcher Implementation"
    publish / skip := true
    exportJars := true
    libraryDependencies ++= Seq(
      ivy,
      verify % Test,
      sbtIo % Test,
      scalacheck % Test,
      junit % Test,
    )
    testFrameworks += new TestFramework("verify.runner.Framework")
    Test / compile := {
      val ignore = (testSamples / publishLocal).value
      val ignore2 = (launchInterfaceSub / publishLocal).value
      (compile in Test).value
    }
    Proguard / proguardOptions ++= Seq(
      "-keep,allowoptimization,allowshrinking class * { *; }", // no obfuscation
      "-keepattributes SourceFile,LineNumberTable", // preserve debugging information
      "-dontnote",
      "-dontwarn",
      "-ignorewarnings")

    keepFullClasses := "xsbti.**" :: Nil
    Proguard / proguardOptions ++= keepFullClasses.value map ("-keep public class " + _ + " {\n\tpublic protected * ;\n}")
    Proguard / proguardInputFilter := { file =>
      file.name match {
        case x if x.startsWith("scala-library") => Some(libraryFilter)
        case x if x.startsWith("ivy-2.3.0")     => Some(ivyFilter)
        case x if x.startsWith("launcher-implementation") => None
        case _                                  => Some(generalFilter)
      }
    }
    Proguard / proguardOptions += ProguardOptions.keepMain("xsbt.boot.Boot")
    mimaPreviousArtifacts := Set.empty
  })

def generalFilter = "!META-INF/**,!*.properties"

def libraryFilter = "!META-INF/**,!*.properties,!scala/util/parsing/**,**.class"

def ivyFilter = {
  def excludeString(s: List[String]) = s.map("!" + _).mkString(",")
  val ivyResources =
    "META-INF/**" ::
      "fr/**" ::
      "**/antlib.xml" ::
      "**/*.png" ::
      "org/apache/ivy/core/settings/ivyconf*.xml" ::
      "org/apache/ivy/core/settings/ivysettings-*.xml" ::
      "org/apache/ivy/plugins/resolver/packager/*" ::
      "**/ivy_vfs.xml" ::
      "org/apache/ivy/plugins/report/ivy-report-*" ::
      "org/apache/ivy/ant/**" ::
      Nil
  excludeString(ivyResources)
}


// used to test the retrieving and loading of an application: sample app is packaged and published to the local repository
lazy val testSamples = (project in file("test-sample"))
  .dependsOn(launchInterfaceSub)
  .settings(Release.javaVersionCheckSettings)
  .settings(nocomma {
    name := "Launch Test"
    publish := { () }
    publishSigned := { () }
    libraryDependencies += scalaCompiler.value
  })
