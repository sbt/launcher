import sbt._
import Keys._
import Path._

object Transform {
  lazy val transformSources = TaskKey[Seq[File]]("transform-sources")
  lazy val inputSourceDirectories = SettingKey[Seq[File]]("input-source-directories")
  lazy val inputSourceDirectory = SettingKey[File]("input-source-directory")
  lazy val inputSources = TaskKey[Seq[File]]("input-sources")
  lazy val sourceProperties = TaskKey[Map[String, String]]("source-properties")

  lazy val transformResources = TaskKey[Seq[File]]("transform-resources")
  lazy val inputResourceDirectories = SettingKey[Seq[File]]("input-resource-directories")
  lazy val inputResourceDirectory = SettingKey[File]("input-resource-directory")
  lazy val inputResources = TaskKey[Seq[File]]("input-resources")
  lazy val resourceProperties = TaskKey[Map[String, String]]("resource-properties")

  lazy val conscriptConfigs = TaskKey[Unit]("conscript-configs")

  def conscriptSettings(launch: Reference) = Seq(
    conscriptConfigs := {
      val res = (launch / Compile / managedResources).value
      val src = (Compile / sourceDirectory).value
      val source = res.filter(_.getName == "sbt.boot.properties").headOption getOrElse sys.error(
        "No managed boot.properties file."
      )
      copyConscriptProperties(source, src / "conscript")
      ()
    }
  )
  def copyConscriptProperties(source: File, conscriptBase: File): Seq[File] = {
    IO.delete(conscriptBase)
    val pairs = Seq(
      "sbt.xMain" -> "sbt",
      "sbt.ScriptMain" -> "scalas",
      "sbt.ConsoleMain" -> "screpl"
    )
    for ((main, dir) <- pairs) yield {
      val file = conscriptBase / dir / "launchconfig"
      copyPropertiesFile(source, main, file)
      file
    }
  }
  def copyPropertiesFile(source: File, newMain: String, target: File): Unit = {
    def subMain(line: String): String =
      if (line.trim.startsWith("class:")) "  class: " + newMain else line
    IO.writeLines(target, IO.readLines(source) map subMain)
  }

  def crossGenSettings = transSourceSettings ++ Seq(
    sourceProperties := Map("cross.package0" -> "sbt", "cross.package1" -> "cross")
  )
  def transSourceSettings = Seq(
    inputSourceDirectory := sourceDirectory.value / "input_sources",
    inputSourceDirectories := Seq(inputSourceDirectory.value),
    inputSources := (inputSourceDirectories.value ** (-DirectoryFilter)).get,
    transformSources / fileMappings := transformSourceMappings.value,
    transformSources := {
      (transformSources / fileMappings).value.map {
        case (in, out) => transform(in, out, sourceProperties.value)
      }
    },
    sourceGenerators += transformSources.taskValue
  )
  def transformSourceMappings = Def.task {
    val ss = inputSources.value
    val sdirs = inputSourceDirectories.value
    val sm = sourceManaged.value
    (ss --- sdirs) pair (rebase(sdirs, sm) | flat(sm)) toSeq
  }
  def configSettings = transResourceSettings ++ Seq(
    resourceProperties := {
      Map(
        "org" -> organization.value,
        "sbt.version" -> version.value,
        "sbt.name" -> name.value,
        "scala.version" -> scalaVersion.value,
        "repositories" -> repositories(isSnapshot.value).mkString(IO.Newline)
      )
    }
  )
  def transResourceSettings = Seq(
    inputResourceDirectory := sourceDirectory.value / "input_resources",
    inputResourceDirectories := Seq(inputResourceDirectory.value),
    inputResources := (inputResourceDirectories.value ** (-DirectoryFilter)).get,
    transformResources / fileMappings := transformResourceMappings.value,
    transformResources := {
      (transformResources / fileMappings).value.map {
        case (in, out) => transform(in, out, resourceProperties.value)
      }
    },
    resourceGenerators += transformResources.taskValue
  )
  def transformResourceMappings = Def.task {
    val rs = inputResources.value
    val rdirs = inputResourceDirectories.value
    val rm = resourceManaged.value
    (rs --- rdirs) pair (rebase(rdirs, rm) | flat(rm)) toSeq
  }

  def transform(in: File, out: File, map: Map[String, String]): File = {
    def get(key: String): String =
      map.getOrElse(key, sys.error("No value defined for key '" + key + "'"))
    val newString = Property.replaceAllIn(IO.read(in), mtch => get(mtch.group(1)))
    if (Some(newString) != read(out))
      IO.write(out, newString)
    out
  }
  def read(file: File): Option[String] =
    try {
      Some(IO.read(file))
    } catch { case _: java.io.IOException => None }
  lazy val Property = """\$\{\{([\w.-]+)\}\}""".r

  def repositories(isSnapshot: Boolean) = Releases :: (if (isSnapshot) Snapshots :: Nil else Nil)
  lazy val Releases = typesafeRepository("releases")
  lazy val Snapshots = typesafeRepository("snapshots")
  def typesafeRepository(status: String) =
    """  typesafe-ivy-%s: https://repo.typesafe.com/typesafe/ivy-%<s/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly""" format status
}
