import sbt.*
import Keys.*
import sbt.internal.inc.Analysis
import xsbti.compile.CompileAnalysis

object Util {
  val publishSigned =
    TaskKey[Unit]("publish-signed", "Publishing all artifacts, but SIGNED using PGP.")

  def commonSettings(nameString: String) = Seq(
    name := nameString,
    resolvers += Resolver.typesafeIvyRepo("releases"),
    publishMavenStyle := true
  )

  /** Configures a project to be java only. */
  lazy val javaOnly = Seq[Setting[?]](
    /*crossPaths := false, */ compileOrder := CompileOrder.JavaThenScala,
    Compile / unmanagedSourceDirectories := Seq((Compile / javaSource).value),
    autoScalaLibrary := false
  )
  lazy val base: Seq[Setting[?]] = baseScalacOptions ++ Licensed.settings
  lazy val baseScalacOptions = Seq(
    scalacOptions ++= {
      Seq(
        "-deprecation",
        "-feature",
        "-language:implicitConversions",
        "-language:postfixOps",
        "-language:existentials",
        "-Werror",
        "-Wunused:all",
      )
    },
  )

  def lastCompilationTime(analysis: Analysis): Long = {
    val lastCompilation = analysis.compilations.allCompilations.lastOption
    lastCompilation.map(_.getStartTime) getOrElse 0L
  }
  def generateVersionFile(
      fileName: String
  )(version: String, dir: File, s: TaskStreams, a0: CompileAnalysis): Seq[File] = {
    import java.util.{ Date, TimeZone }
    val analysis = a0 match { case a: Analysis => a }
    val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timestamp = formatter.format(new Date)
    val content = versionLine(version) + "\ntimestamp=" + timestamp
    val f = dir / fileName
    if (
      !f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)
    ) {
      s.log.info("Writing version information to " + f + " :\n" + content)
      IO.write(f, content)
    }
    f :: Nil
  }
  def versionLine(version: String): String = "version=" + version
  def containsVersion(propFile: File, version: String): Boolean =
    IO.read(propFile).contains(versionLine(version))
}

object Licensed {
  lazy val notice = SettingKey[File]("notice")
  lazy val extractLicenses = TaskKey[Seq[File]]("extract-licenses")

  lazy val seeRegex = """\(see (.*?)\)""".r
  def licensePath(base: File, str: String): File = {
    val path = base / str;
    if (path.exists) path else sys.error("Referenced license '" + str + "' not found at " + path)
  }
  def seePaths(base: File, noticeString: String): Seq[File] =
    seeRegex.findAllIn(noticeString).matchData.map(d => licensePath(base, d.group(1))).toList

  def settings: Seq[Setting[?]] = Seq(
    notice := baseDirectory.value / "NOTICE",
    Compile / unmanagedResources ++= (notice.value +: extractLicenses.value),
    extractLicenses := extractLicenses0(
      (ThisBuild / baseDirectory).value,
      notice.value,
      streams.value
    )
  )
  def extractLicenses0(base: File, note: File, s: TaskStreams): Seq[File] =
    if (!note.exists) Nil
    else
      try {
        seePaths(base, IO.read(note))
      } catch {
        case e: Exception => s.log.warn("Could not read NOTICE"); Nil
      }
}
