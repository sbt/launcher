package xsbt.boot

import java.io.{ File, InputStream }
import java.util.Properties
import xsbti._
import LaunchTest._
import sbt.io.IO.{ createDirectory, touch, withTemporaryDirectory }

object ScalaProviderTest extends verify.BasicTestSuite {
  test("Launch should provide ClassLoader for Scala 2.8.2") {
    checkScalaLoader("2.8.2")
  }

  test("Launch should provide ClassLoader for Scala 2.9.0") {
    checkScalaLoader("2.9.0")
  }

  test("Launch should provide ClassLoader for Scala 2.9.2") {
    checkScalaLoader("2.9.2")
  }

  test("Launch should provide ClassLoader for Scala 2.10.7") {
    checkScalaLoader("2.10.7")
  }

  test("Launch should provide ClassLoader for Scala 2.11.12") {
    checkScalaLoader("2.11.12")
  }

  test("Launch should successfully load an application from local repository and run it with correct arguments") {
    assert(checkLoad(List("test"), "xsbt.boot.test.ArgumentTest").asInstanceOf[Exit].code == 0)
    intercept[RuntimeException] {
      checkLoad(List(), "xsbt.boot.test.ArgumentTest")
      ()
    }
  }

  test("Launch should successfully load an plain application from local repository and run it with correct arguments") {
    assert(checkLoad(List("test"), "xsbt.boot.test.PlainArgumentTest").asInstanceOf[Exit].code == 0)
    intercept[RuntimeException] {
      checkLoad(List(), "xsbt.boot.test.PlainArgumentTest")
      ()
    }
  }

  test("Launch should successfully load an application instead of the plain application") {
    assert(checkLoad(List(), "xsbt.boot.test.PriorityTest").asInstanceOf[Exit].code == 0)
  }

  test("Launch should successfully load an application from local repository and run it with correct sbt version") {
    assert(checkLoad(List(AppVersion), "xsbt.boot.test.AppVersionTest").asInstanceOf[Exit].code == 0)
  }

  test("Launch should add extra resources to the classpath") {
    assert(checkLoad(testResources, "xsbt.boot.test.ExtraTest", createExtra).asInstanceOf[Exit].code == 0)
  }

  def checkLoad(arguments: List[String], mainClassName: String): MainResult =
    checkLoad(arguments, mainClassName, _ => Array[File]())

  def checkLoad(arguments: List[String], mainClassName: String, extra: File => Array[File]): MainResult =
    withTemporaryDirectory { currentDirectory =>
      withLauncher { launcher =>
        Launch.run(launcher)(
          new RunConfiguration(Some(unmapScalaVersion(LaunchTest.getScalaVersion)), LaunchTest.testApp(mainClassName, extra(currentDirectory)).toID, currentDirectory, arguments)
        )
      }
    }

  private def testResources = List("test-resourceA", "a/b/test-resourceB", "sub/test-resource")

  private def createExtra(currentDirectory: File) =
    {
      val resourceDirectory = new File(currentDirectory, "resources")
      createDirectory(resourceDirectory)
      testResources.foreach(resource => touch(new File(resourceDirectory, resource.replace('/', File.separatorChar))))
      Array(resourceDirectory)
    }

  private def checkScalaLoader(version: String) = withLauncher(checkLauncher(version, mapScalaVersion(version)))

  private def checkLauncher(version: String, versionValue: String)(launcher: Launcher) = {
    import scala.language.reflectiveCalls
    val provider = launcher.getScala(version)
    val loader = provider.loader
    // ensure that this loader can load Scala classes by trying scala.ScalaObject.
    tryScala(loader, loader.getParent)
    assert(getScalaVersion(loader) == versionValue)

    val libraryLoader = provider.loader.getParent
    // Test the structural type
    libraryLoader match {
      case x: ClassLoader with LibraryLoader @unchecked =>
        assert(x.scalaVersion == version)
    }
    tryScala(libraryLoader, libraryLoader)
  }

  private def tryScala(loader: ClassLoader, libraryLoader: ClassLoader) =
    assert(Class.forName("scala.Product", false, loader).getClassLoader == libraryLoader)

  type LibraryLoader = { def scalaVersion: String }
}

object LaunchTest {
  def testApp(main: String): Application = testApp(main, Array[File]())
  def testApp(main: String, extra: Array[File]): Application = Application("org.scala-sbt", new Explicit("launch-test"), new Explicit(AppVersion), main, Nil, CrossValue.Disabled, extra)
  import Predefined._
  def testRepositories = List(Local, MavenCentral, SonatypeOSSSnapshots).map(Repository.Predefined(_))
  def withLauncher[T](f: xsbti.Launcher => T): T =
    withTemporaryDirectory { bootDirectory =>
      f(Launcher(bootDirectory, testRepositories))
    }

  val finalStyle = Set("2.9.1", "2.9.0-1", "2.9.0", "2.8.2", "2.8.1", "2.8.0")
  def unmapScalaVersion(versionNumber: String) = versionNumber.stripSuffix(".final")
  def mapScalaVersion(versionNumber: String) = if (finalStyle(versionNumber)) versionNumber + ".final" else versionNumber

  def getScalaVersion: String = getScalaVersion(getClass.getClassLoader)
  def getScalaVersion(loader: ClassLoader): String = getProperty(loader, "library.properties", "version.number")
  lazy val AppVersion = getProperty(getClass.getClassLoader, "sbt.launcher.version.properties", "version")

  private[this] def getProperty(loader: ClassLoader, res: String, prop: String) = loadProperties(loader.getResourceAsStream(res)).getProperty(prop)
  private[this] def loadProperties(propertiesStream: InputStream): Properties =
    {
      val properties = new Properties
      try { properties.load(propertiesStream) } finally { propertiesStream.close() }
      properties
    }
}
