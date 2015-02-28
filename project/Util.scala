import sbt._
import Keys._
import StringUtilities.normalize

object Util {

  def noPublish(p: Project) = p.settings(noRemotePublish:_*)
  def noRemotePublish: Seq[Setting[_]] =
    // TODO - publishSigned
    Seq(publish := ())
  def commonSettings(nameString: String) = Seq(
    name := nameString,
    resolvers += Resolver.typesafeIvyRepo("releases")
  )

  def minProject(path: File, nameString: String) = Project(normalize(nameString), path) settings (commonSettings(nameString) ++ Release.javaVersionCheckSettings: _*)
  def baseProject(path: File, nameString: String) = minProject(path, nameString) settings (base: _*)

  /** Configures a project to be java only. */
  lazy val javaOnly = Seq[Setting[_]](
     /*crossPaths := false, */ compileOrder := CompileOrder.JavaThenScala,
    unmanagedSourceDirectories in Compile := Seq((javaSource in Compile).value),
    autoScalaLibrary := false
  )
  lazy val base: Seq[Setting[_]] = baseScalacOptions ++ Licensed.settings
  lazy val baseScalacOptions = Seq(
    scalacOptions ++= Seq("-Xelide-below", "0"),
    scalacOptions <++= scalaVersion map CrossVersion.partialVersion map {
      case Some((2, 9)) => Nil // support 2.9 for some subprojects for the Scala Eclipse IDE
      case _            => Seq("-feature", "-language:implicitConversions", "-language:postfixOps", "-language:higherKinds", "-language:existentials")
    },
    scalacOptions <++= scalaVersion map CrossVersion.partialVersion map {
      case Some((2, 10)) => Seq("-deprecation", "-Xlint")
      case _             => Seq()
    }
  )
  lazy val minimalSettings: Seq[Setting[_]] = Defaults.paths ++ Seq[Setting[_]](crossTarget := target.value, name <<= thisProject(_.id))


  def lastCompilationTime(analysis: sbt.inc.Analysis): Long =
    {
      val lastCompilation = analysis.compilations.allCompilations.lastOption
      lastCompilation.map(_.startTime) getOrElse 0L
    }
  def generateVersionFile(fileName: String)(version: String, dir: File, s: TaskStreams, analysis: sbt.inc.Analysis): Seq[File] =
    {
      import java.util.{ Date, TimeZone }
      val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
      formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
      val timestamp = formatter.format(new Date)
      val content = versionLine(version) + "\ntimestamp=" + timestamp
      val f = dir / fileName
      if (!f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)) {
        s.log.info("Writing version information to " + f + " :\n" + content)
        IO.write(f, content)
      }
      f :: Nil
    }
  def versionLine(version: String): String = "version=" + version
  def containsVersion(propFile: File, version: String): Boolean = IO.read(propFile).contains(versionLine(version))
}
object Licensed {
  lazy val notice = SettingKey[File]("notice")
  lazy val extractLicenses = TaskKey[Seq[File]]("extract-licenses")

  lazy val seeRegex = """\(see (.*?)\)""".r
  def licensePath(base: File, str: String): File = { val path = base / str; if (path.exists) path else sys.error("Referenced license '" + str + "' not found at " + path) }
  def seePaths(base: File, noticeString: String): Seq[File] = seeRegex.findAllIn(noticeString).matchData.map(d => licensePath(base, d.group(1))).toList

  def settings: Seq[Setting[_]] = Seq(
    notice <<= baseDirectory(_ / "NOTICE"),
    unmanagedResources in Compile <++= (notice, extractLicenses) map { _ +: _ },
    extractLicenses <<= (baseDirectory in ThisBuild, notice, streams) map extractLicenses0
  )
  def extractLicenses0(base: File, note: File, s: TaskStreams): Seq[File] =
    if (!note.exists) Nil else
      try { seePaths(base, IO.read(note)) }
      catch { case e: Exception => s.log.warn("Could not read NOTICE"); Nil }
}
