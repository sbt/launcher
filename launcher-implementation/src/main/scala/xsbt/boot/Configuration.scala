/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.{ File, InputStreamReader }
import java.net.{ MalformedURLException, URI, URL }
import java.util.regex.Pattern
import scala.collection.immutable.List
import annotation.tailrec

object ConfigurationStorageState extends Enumeration {
  val PropertiesFile = value("properties-file")
  val SerializedFile = value("serialized-file")
}

object Configuration {
  import ConfigurationStorageState._
  final val SysPropPrefix = "-D"
  def parse(file: URL, baseDirectory: File) =
    Using(new InputStreamReader(file.openStream, "utf8"))((new ConfigurationParser).apply)

  /**
   * Finds the configuration location.
   *
   * Note:  Configuration may be previously serialized by a launcher.
   */
  @tailrec def find(
      args: List[String],
      baseDirectory: File
  ): (URL, List[String], ConfigurationStorageState.Value) =
    args match {
      case head :: tail if head.startsWith("@load:") =>
        (directConfiguration(head.substring(6), baseDirectory), tail, SerializedFile)
      case head :: tail if head.startsWith("@") =>
        (directConfiguration(head.substring(1), baseDirectory), tail, PropertiesFile)
      case head :: tail if head.startsWith(SysPropPrefix) =>
        setProperty(head stripPrefix SysPropPrefix)
        find(tail, baseDirectory)
      case _ =>
        val propertyConfigured = System.getProperty("sbt.boot.properties")
        val url =
          if (propertyConfigured == null) configurationOnClasspath
          else configurationFromFile(propertyConfigured, baseDirectory)
        (url, args, PropertiesFile)
    }
  def setProperty(head: String): Unit = {
    head.split("=", 2) match {
      case Array("")         => Console.err.println(s"[warn] [launcher] invalid system property '$head'")
      case Array(key)        => sys.props += key -> ""
      case Array(key, value) => sys.props += key -> value
      case _                 => ()
    }
    ()
  }
  def configurationOnClasspath: URL = {
    val paths = resourcePaths(guessSbtVersion)
    paths.iterator.map(getClass.getResource).find(neNull) getOrElse
      (multiPartError("could not find sbt launch configuration. searched classpath for:", paths))
  }
  def directConfiguration(path: String, baseDirectory: File): URL = {
    try {
      new URL(path)
    } catch { case _: MalformedURLException => configurationFromFile(path, baseDirectory) }
  }
  def configurationFromFile(path: String, baseDirectory: File): URL = {
    val pathURI = filePathURI(path)
    def resolve(against: URI): Option[URL] = {
      val resolved = against.resolve(pathURI) // variant that accepts String doesn't properly escape (#725)
      val exists = try {
        (new File(resolved)).exists
      } catch { case _: IllegalArgumentException => false }
      if (exists) Some(resolved.toURL) else None
    }
    val against = resolveAgainst(baseDirectory)
    // use Iterators so that resolution occurs lazily, for performance
    val resolving = against.iterator.flatMap(e => resolve(e).toList.iterator)
    if (!resolving.hasNext)
      multiPartError("could not find configuration file '" + path + "'. searched:", against)
    resolving.next()
  }
  def multiPartError[A](firstLine: String, lines: List[A]) =
    Pre.error((firstLine :: lines.map(_.toString())).mkString("\n\t"))

  def UnspecifiedVersionPart = "Unspecified"
  def DefaultVersionPart = "Default"
  def DefaultBuildProperties = "project/build.properties"
  def SbtVersionProperty = "sbt.version"
  val ConfigurationName = "sbt.boot.properties"
  val JarBasePath = "/sbt/"
  def userConfigurationPath = "/" + ConfigurationName
  def defaultConfigurationPath = JarBasePath + ConfigurationName
  val baseResourcePaths: List[String] = userConfigurationPath :: defaultConfigurationPath :: Nil
  def resourcePaths(sbtVersion: Option[String]): List[String] =
    versionParts(sbtVersion) flatMap { part =>
      baseResourcePaths map { base =>
        base + part
      }
    }
  def fallbackParts: List[String] = "" :: Nil
  def versionParts(version: Option[String]): List[String] =
    version match {
      case None    => UnspecifiedVersionPart :: fallbackParts
      case Some(v) => versionParts(v)
    }
  def versionParts(version: String): List[String] = {
    val pattern = Pattern.compile("""(\d+)(\.\d+)(\.\d+)(-.*)?""")
    val m = pattern.matcher(version)
    if (m.matches())
      subPartsIndices flatMap { is =>
        fullMatchOnly(is.map(m.group))
      } else
      noMatchParts
  }
  def noMatchParts: List[String] = DefaultVersionPart :: fallbackParts
  private[this] def fullMatchOnly(groups: List[String]): Option[String] =
    if (groups.forall(neNull)) Some(groups.mkString) else None

  private[this] def subPartsIndices =
    (1 :: 2 :: 3 :: 4 :: Nil) ::
      (1 :: 2 :: 3 :: Nil) ::
      (1 :: 2 :: Nil) ::
      (Nil) ::
      Nil

  // the location of project/build.properties and the name of the property within that file
  //  that configures the sbt version is configured in sbt.boot.properties.
  // We have to hard code them here in order to use them to determine the location of sbt.boot.properties itself
  def guessSbtVersion: Option[String] = {
    val props = Pre.readProperties(new File(DefaultBuildProperties))
    Option(props.getProperty(SbtVersionProperty))
  }

  def resolveAgainst(baseDirectory: File): List[URI] =
    directoryURI(baseDirectory) ::
      directoryURI(new File(System.getProperty("user.home"))) ::
      toDirectory(classLocation(getClass).toURI) ::
      Nil

  def classLocation(cl: Class[_]): URL = {
    val codeSource = cl.getProtectionDomain.getCodeSource
    if (codeSource == null) Pre.error("no class location for " + cl)
    else codeSource.getLocation
  }
  // single-arg constructor doesn't properly escape
  def filePathURI(path: String): URI = {
    if (path.startsWith("file:")) new URI(path)
    else {
      val f = new File(path)
      new URI(if (f.isAbsolute) "file" else null, path, null)
    }
  }
  def directoryURI(dir: File): URI = directoryURI(dir.toURI)
  def directoryURI(uri: URI): URI = {
    assert(uri.isAbsolute)
    val str = uri.toASCIIString
    val dirStr = if (str.endsWith("/")) str else str + "/"
    (new URI(dirStr)).normalize
  }

  def toDirectory(uri: URI): URI =
    try {
      val file = new File(uri)
      val newFile = if (file.isFile) file.getParentFile else file
      directoryURI(newFile)
    } catch { case _: Exception => uri }
  private[this] def neNull: AnyRef => Boolean = _ ne null
}
