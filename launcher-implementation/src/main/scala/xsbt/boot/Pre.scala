/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

import java.io.{ File, FileFilter }
import java.net.URL
import java.util.Locale
import scala.annotation.tailrec
import scala.collection.immutable.List
import scala.reflect.ClassTag

object Pre:
  def readLine(prompt: String): Option[String] =
    val c = System.console()
    if c eq null then None else Option(c.readLine(prompt))
  def trimLeading(line: String) =
    @tailrec
    def newStart(i: Int): Int =
      if i >= line.length || !Character.isWhitespace(line.charAt(i)) then i else newStart(i + 1)
    line.substring(newStart(0))
  def isEmpty(line: String) = line.length == 0
  def isNonEmpty(line: String) = line.length > 0
  def assert(condition: Boolean, msg: => String): Unit =
    if !condition then throw new AssertionError(msg)
  def assert(condition: Boolean): Unit = assert(condition, "assertion failed")
  def require(condition: Boolean, msg: => String): Unit =
    if !condition then throw new IllegalArgumentException(msg)
  def error(msg: String): Nothing = throw new BootException(prefixError(msg))
  def declined(msg: String): Nothing = throw new BootException(msg)
  def prefixError(msg: String): String = "error during sbt launcher: " + msg
  def toBoolean(s: String) = java.lang.Boolean.parseBoolean(s)

  def toArray[T: ClassTag](list: List[T]) =
    val arr = new Array[T](list.length)
    @tailrec
    def copy(i: Int, rem: List[T]): Unit =
      if i < arr.length then
        arr(i) = rem.head
        copy(i + 1, rem.tail)
    copy(0, list)
    arr
  /* These exist in order to avoid bringing in dependencies on RichInt and ArrayBuffer, among others. */
  def concat(a: Array[File], b: Array[File]): Array[File] =
    val n = new Array[File](a.length + b.length)
    java.lang.System.arraycopy(a, 0, n, 0, a.length)
    java.lang.System.arraycopy(b, 0, n, a.length, b.length)
    n
  def array(files: File*): Array[File] = toArray(files.toList)
  /* Saves creating a closure for default if it has already been evaluated*/
  def orElse[T](opt: Option[T], default: T) = if opt.isDefined then opt.get else default

  def wrapNull(a: Array[File]): Array[File] = if a == null then new Array[File](0) else a
  def const[B](b: B): Any => B = _ => b
  def strictOr[T](a: Option[T], b: Option[T]): Option[T] = a match
    case None => b;
    case _    => a
  def getOrError[T](a: Option[T], msg: String): T = a match
    case None    => error(msg);
    case Some(x) => x
  def orNull[T >: Null](t: Option[T]): T = t match
    case None    => null;
    case Some(x) => x

  def getJars(directories: List[File]): Array[File] =
    toArray(directories.flatMap(directory => wrapNull(directory.listFiles(JarFilter))))

  object JarFilter extends FileFilter:
    def accept(file: File) = !file.isDirectory && file.getName.endsWith(".jar")
  def getMissing(loader: ClassLoader, classes: Iterable[String]): Iterable[String] =
    def classMissing(c: String) =
      try
        Class.forName(c, false, loader); false
      catch case _: ClassNotFoundException => true
    classes.toList.filter(classMissing)
  def toURLs(files: Array[File]): Array[URL] = files.map(_.toURI.toURL)
  def toFile(url: URL): File =
    try new File(url.toURI)
    catch case _: java.net.URISyntaxException => new File(url.getPath)

  def delete(f: File): Unit =
    if f.isDirectory then
      val fs = f.listFiles()
      if fs ne null then fs foreach delete
    if f.exists then f.delete()
    ()
  final val isWindows: Boolean =
    System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  final val isCygwin: Boolean = isWindows && java.lang.Boolean.getBoolean("sbt.cygwin")

  private[boot] def substituteTilde(path: String): String =
    path.replaceFirst(
      "^~(/|\\\\|$)",
      java.util.regex.Matcher.quoteReplacement(userHome.toString) + "$1"
    )

  /**
   * User home directory. JDK reports `user.home` as `?` when the current user has no passwd
   * entry (common in Docker; see JDK-8280357), so fall back to the `HOME` environment variable,
   * then `java.io.tmpdir`.
   */
  def userHome: File =
    new File(
      userHomeFrom(sys.props.get("user.home"), sys.env.get("HOME"), sys.props("java.io.tmpdir"))
    )

  private[boot] def isValidHome(s: String): Boolean = s.trim.nonEmpty && s.trim != "?"

  private[boot] def userHomeFrom(
      userHomeProp: Option[String],
      homeEnv: Option[String],
      tmpDir: String
  ): String =
    userHomeProp.filter(isValidHome).orElse(homeEnv.filter(isValidHome)).getOrElse(tmpDir)

  /**
   * Sets the `user.home` system property to [[userHome]] when it is invalid, so that
   * boot properties and libraries such as Coursier and JNA see the same directory.
   */
  def fixUserHome(): Unit =
    if !sys.props.get("user.home").exists(isValidHome) then
      System.setProperty("user.home", userHome.toString)

  import java.util.Properties
  import java.io.{ FileInputStream, FileOutputStream }
  private[boot] def readProperties(propertiesFile: File) =
    val properties = new Properties
    if propertiesFile.exists then Using(new FileInputStream(propertiesFile))(properties.load)
    properties
  private[boot] def writeProperties(properties: Properties, file: File, msg: String): Unit =
    file.getParentFile.mkdirs()
    Using(new FileOutputStream(file))(out => properties.store(out, msg))
  private[boot] def setSystemProperties(properties: Properties): Unit =
    val nameItr = properties.stringPropertyNames.iterator
    while nameItr.hasNext do
      val propName = nameItr.next
      System.setProperty(propName, properties.getProperty(propName))
