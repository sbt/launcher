import sbt.*
import Keys.*
import Path.*

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

  def transSourceSettings = Seq(
    inputSourceDirectory := sourceDirectory.value / "input_sources",
    inputSourceDirectories := Seq(inputSourceDirectory.value),
    inputSources := (inputSourceDirectories.value ** (-DirectoryFilter)).get(),
    transformSources / fileMappings := transformSourceMappings.value,
    transformSources := {
      (transformSources / fileMappings).value.map { case (in, out) =>
        transform(in, out, sourceProperties.value)
      }
    },
    sourceGenerators += transformSources.taskValue
  )
  def transformSourceMappings = Def.task {
    val ss = inputSources.value
    val sdirs = inputSourceDirectories.value
    val sm = sourceManaged.value
    (ss --- sdirs).pair(rebase(sdirs, sm) | flat(sm))
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
    inputResources := (inputResourceDirectories.value ** (-DirectoryFilter)).get(),
    transformResources / fileMappings := transformResourceMappings.value,
    transformResources := {
      (transformResources / fileMappings).value.map { case (in, out) =>
        transform(in, out, resourceProperties.value)
      }
    },
    resourceGenerators += transformResources.taskValue
  )
  def transformResourceMappings = Def.task {
    val rs = inputResources.value
    val rdirs = inputResourceDirectories.value
    val rm = resourceManaged.value
    (rs --- rdirs).pair(rebase(rdirs, rm) | flat(rm))
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
