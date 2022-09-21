/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package xsbt.boot

import Pre._
import BootConfiguration.{ CompilerModuleName, JAnsiVersion, LibraryModuleName }
import java.io.File
import java.net.{ URI, URL, URLClassLoader }
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

import scala.collection.immutable.List
import scala.annotation.{ nowarn, tailrec }
import ConfigurationStorageState._

class LauncherArguments(val args: List[String], val isLocate: Boolean, val isExportRt: Boolean)

object Launch {
  def apply(arguments: LauncherArguments): Option[Int] =
    apply((new File("")).getAbsoluteFile, arguments)

  def apply(currentDirectory: File, arguments: LauncherArguments): Option[Int] =
    if (arguments.isExportRt) {
      if (arguments.args.size != 1) {
        sys.error("destination expected: --export-rt <dest>")
      }
      exportRt(arguments.args.head)
    } else {
      val (configLocation, newArgs2, state) = Configuration.find(arguments.args, currentDirectory)
      val config = state match {
        case SerializedFile => LaunchConfiguration.restore(configLocation)
        case PropertiesFile => parseAndInitializeConfig(configLocation, currentDirectory)
      }
      if (arguments.isLocate) {
        if (!newArgs2.isEmpty) {
          // TODO - Print the arguments without exploding proguard size.
          Console.err.println("[warn] [launcher] --locate option ignores arguments")
        }
        locate(currentDirectory, config)
      } else {
        // First check to see if there are java system properties we need to set. Then launch the application.
        updateProperties(config)
        launch(run(Launcher(config)))(makeRunConfig(currentDirectory, config, newArgs2))
      }
    }

  def exportRt(destination: String): Option[Int] = {
    import java.nio.file._
    import java.util.HashMap
    val fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"))
    val path: Path = fileSystem.getPath("/modules")
    val destPath: Path = Paths.get(destination)
    val uri = URI.create("jar:" + destPath.toUri())
    val env = new HashMap[String, String]()
    env.put("create", "true")
    val zipfs = FileSystems.newFileSystem(uri, env)
    try {
      val iterator = Files.list(path).iterator()
      while (iterator.hasNext()) {
        val next = iterator.next()
        IO.copyDirectory(next, zipfs.getPath("/"))
      }
    } finally {
      zipfs.close
    }
    Some(0)
  }

  /** Locate a server, print where it is, and exit. */
  def locate(currentDirectory: File, config: LaunchConfiguration): Option[Int] = {
    config.serverConfig match {
      case Some(_) =>
        val uri = ServerLocator.locate(currentDirectory, config)
        Console.err.println(uri.toASCIIString)
        Some(0)
      case None =>
        sys.error(s"${config.app.groupID}-${config.app.main} is not configured as a server.")
    }
  }

  /**
   * Some hackery to allow sys.props to be configured via a file. If this launch config has
   * a valid file configured, we load the properties and and apply them to this jvm.
   */
  def updateProperties(config: LaunchConfiguration): Unit = {
    config.serverConfig match {
      case Some(config) =>
        config.jvmPropsFile match {
          case Some(file) if file.exists =>
            try setSystemProperties(readProperties(file))
            catch {
              case e: Exception =>
                throw new RuntimeException(s"unable to load server properties file: ${file}", e)
            }
          case _ =>
        }
      case None =>
    }
  }

  /** Parses the configuration *and* runs the initialization code that will remove variable references. */
  def parseAndInitializeConfig(configLocation: URL, currentDirectory: File): LaunchConfiguration = {
    val (parsed, bd) = parseConfiguration(configLocation, currentDirectory)
    resolveConfig(parsed)
  }

  /** Parse configuration and return it and the baseDirectory of the launch. */
  def parseConfiguration(configLocation: URL, currentDirectory: File): (LaunchConfiguration, File) =
    Find(Configuration.parse(configLocation, currentDirectory), currentDirectory)

  /** Setups the Initialize object so we can fill in system properties in the configuration */
  def resolveConfig(parsed: LaunchConfiguration): LaunchConfiguration = {
    // Set up initialize.
    val propertiesFile = parsed.boot.properties
    import parsed.boot.{ enableQuick, promptCreate, promptFill }
    if (isNonEmpty(promptCreate) && !propertiesFile.exists)
      Initialize.create(propertiesFile, promptCreate, enableQuick, parsed.appProperties)
    else if (promptFill)
      Initialize.fill(propertiesFile, parsed.appProperties)

    parsed.logging.debug("parsed configuration: " + parsed)
    val resolved = ResolveValues(parsed)
    resolved.logging.debug("resolved configuration: " + resolved)
    resolved
  }

  /** Create run configuration we'll use to launch the app. */
  def makeRunConfig(
      currentDirectory: File,
      config: LaunchConfiguration,
      arguments: List[String]
  ): RunConfiguration =
    new RunConfiguration(config.getScalaVersion, config.app.toID, currentDirectory, arguments)

  /** The actual mechanism used to run a launched application. */
  def run(launcher: xsbti.Launcher)(config: RunConfiguration): xsbti.MainResult = {
    import config._
    val appProvider
        : xsbti.AppProvider = launcher.app(app, orNull(scalaVersion)) // takes ~40 ms when no update is required
    val appConfig: xsbti.AppConfiguration =
      new AppConfiguration(toArray(arguments), workingDirectory, appProvider)

    // TODO - Jansi probably should be configurable via some other mechanism...
    JAnsi.install(launcher.topLoader)
    try {
      val main = appProvider.newMain()
      try {
        withContextLoader(appProvider.loader)(main.run(appConfig))
      } catch { case e: xsbti.FullReload => if (e.clean) delete(launcher.bootDirectory); throw e }
    } finally {
      JAnsi.uninstall(launcher.topLoader)
    }
  }
  @tailrec
  final def launch(
      run: RunConfiguration => xsbti.MainResult
  )(config: RunConfiguration): Option[Int] = {
    run(config) match {
      case e: xsbti.Exit     => Some(e.code)
      case c: xsbti.Continue => None
      case r: xsbti.Reboot =>
        launch(run)(
          new RunConfiguration(Option(r.scalaVersion), r.app, r.baseDirectory, r.arguments.toList)
        )
      case x =>
        throw new BootException(
          "invalid main result: " + x + (if (x eq null) "" else " (class: " + x.getClass + ")")
        )
    }
  }
  private[this] def withContextLoader[T](loader: ClassLoader)(eval: => T): T = {
    val oldLoader = Thread.currentThread.getContextClassLoader
    Thread.currentThread.setContextClassLoader(loader)
    try {
      eval
    } finally {
      Thread.currentThread.setContextClassLoader(oldLoader)
    }
  }

  // Cache of classes for lookup later.
  val ServerMainClass = classOf[xsbti.ServerMain]
  val AppMainClass = classOf[xsbti.AppMain]
}
final class RunConfiguration(
    val scalaVersion: Option[String],
    val app: xsbti.ApplicationID,
    val workingDirectory: File,
    val arguments: List[String]
)

import BootConfiguration.{
  appDirectoryName,
  baseDirectoryName,
  extractScalaVersion,
  ScalaDirectoryName,
  TestLoadScala2Classes,
  TestLoadScala3Classes,
  ScalaOrg
}
class Launch private[xsbt] (
    val bootDirectory: File,
    val lockBoot: Boolean,
    val ivyOptions: IvyOptions
) extends xsbti.Launcher {
  import ivyOptions.{ checksums => checksumsList, classifiers, repositories }
  bootDirectory.mkdirs
  def getScala(version: String): xsbti.ScalaProvider = getScala(version, "")
  def getScala(version: String, reason: String): xsbti.ScalaProvider =
    getScala(version, reason, ScalaOrg)
  def getScala(version: String, reason: String, scalaOrg: String) =
    scalaProviders((scalaOrg, version), reason)
  def app(id: xsbti.ApplicationID, version: String): xsbti.AppProvider = app(id, Option(version))
  def app(id: xsbti.ApplicationID, scalaVersion: Option[String]): xsbti.AppProvider =
    getAppProvider(id, scalaVersion, false)

  val bootLoader = new BootFilteredLoader(getClass.getClassLoader)

  @nowarn
  private[this] val initLoader: ClassLoader = if (isWindows && !isCygwin) {
    val version = sys.props.get(Configuration.SbtVersionProperty) orElse Configuration.guessSbtVersion
    if (version.fold(false)(_.startsWith("0."))) jansiLoader(bootLoader) else bootLoader
  } else bootLoader

  private[this] val scalaProviderClassLoader = new AtomicReference(initLoader)
  def topLoader: ClassLoader = scalaProviderClassLoader.get()
  private val scalaProviders = new Cache[(String, String), String, xsbti.ScalaProvider](
    (x, y) => getScalaProvider(x._1, x._2, y, scalaProviderClassLoader.get)
  )

  val updateLockFile = if (lockBoot) Some(new File(bootDirectory, "sbt.boot.lock")) else None

  def globalLock: xsbti.GlobalLock = Locks
  def ivyHome = orNull(ivyOptions.ivyHome)
  def ivyRepositories = (repositories: List[xsbti.Repository]).toArray
  def appRepositories = ((repositories filterNot (_.bootOnly)): List[xsbti.Repository]).toArray
  def isOverrideRepositories: Boolean = ivyOptions.isOverrideRepositories
  def checksums = checksumsList.toArray[String]

  // JAnsi needs to be shared between Scala and the application so there aren't two competing versions
  @deprecated("sbt handles jansi management itself", "1.1.6")
  def jansiLoader(parent: ClassLoader): ClassLoader = {
    val id = AppID(
      "org.fusesource.jansi",
      "jansi",
      JAnsiVersion,
      "",
      toArray(Nil),
      xsbti.CrossValue.Disabled,
      array()
    )
    val jansiHome = appDirectory(new File(bootDirectory, baseDirectoryName(ScalaOrg, None)), id)
    val module = appModule(id, None, false, "jansi")
    def makeLoader(): ClassLoader = {
      val urls = toURLs(wrapNull(jansiHome.listFiles(JarFilter)))
      val loader = new URLClassLoader(urls, bootLoader)
      checkLoader(loader, module, "org.fusesource.jansi.internal.WindowsSupport" :: Nil, loader)
    }
    val existingLoader =
      if (jansiHome.exists)
        try Some(makeLoader())
        catch {
          case e: Exception => None
        }
      else
        None
    existingLoader getOrElse {
      update(module, "")
      makeLoader()
    }
  }
  def checkLoader[T](
      loader: ClassLoader,
      module: ModuleDefinition,
      testClasses: Seq[String],
      ifValid: T
  ): T = {
    val missing = getMissing(loader, testClasses)
    if (missing.isEmpty)
      ifValid
    else
      module.retrieveCorrupt(missing)
  }

  private[this] def makeConfiguration(
      scalaOrg: String,
      version: Option[String]
  ): UpdateConfiguration =
    new UpdateConfiguration(
      bootDirectory,
      ivyOptions.ivyHome,
      scalaOrg,
      version,
      repositories,
      checksumsList
    )

  final def getAppProvider(
      id: xsbti.ApplicationID,
      explicitScalaVersion: Option[String],
      forceAppUpdate: Boolean
  ): xsbti.AppProvider =
    locked(new Callable[xsbti.AppProvider] {
      def call = getAppProvider0(id, explicitScalaVersion, forceAppUpdate)
    })

  @tailrec private[this] final def getAppProvider0(
      id: xsbti.ApplicationID,
      explicitScalaVersion0: Option[String],
      forceAppUpdate: Boolean
  ): xsbti.AppProvider = {
    val explicitScalaVersion = explicitScalaVersion0 match {
      case Some(sv) => Some(sv)
      case _        =>
        // https://github.com/sbt/sbt/issues/6587
        // set the Scala version of sbt 1.4.x series to 2.12.12 explicitly
        // since util-interface depends on Scala 2.13 by mistake
        // https://github.com/sbt/sbt/blob/v1.4.0/project/Dependencies.scala
        if (id.groupID() == "org.scala-sbt" &&
            id.name() == "sbt" && id.version().startsWith("1.4.")) Some("2.12.12")
        else None
    }
    val app = appModule(id, explicitScalaVersion, true, "app")

    /** Replace the version of an ApplicationID with the given one, if set. */
    def resolveId(appVersion: Option[String], id: xsbti.ApplicationID) =
      appVersion map { v =>
        import id._
        AppID(
          groupID(),
          name(),
          v,
          mainClass(),
          mainComponents(),
          crossVersionedValue(),
          classpathExtra()
        )
      } getOrElse id
    val baseDirs = (resolvedVersion: Option[String]) =>
      (base: File) => appBaseDirs(base, resolveId(resolvedVersion, id))
    def retrieve() = {
      val (appv, sv) = update(app, "")
      val scalaVersion = strictOr(explicitScalaVersion, sv)
      new RetrievedModule(true, app, sv, appv, baseDirs(appv)(scalaHome(ScalaOrg, scalaVersion)))
    }
    val retrievedApp =
      if (forceAppUpdate)
        retrieve()
      else
        existing(app, ScalaOrg, explicitScalaVersion, baseDirs(None)) getOrElse retrieve()

    val testInterface = java.util.regex.Pattern.compile("test-interface-[0-9.]+\\.jar")
    retrievedApp.fullClasspath.find(f => testInterface.matcher(f.getName).find()).foreach { f =>
      scalaProviderClassLoader.set(new TestInterfaceLoader(f, initLoader, topLoader))
    }
    // https://github.com/sbt/sbt/issues/4955
    // When sbt core artifacts are not properly downloaded, this the point that fails with "No Scala version specified or detected"
    val scalaVersion = getOrError(
      strictOr(explicitScalaVersion, retrievedApp.detectedScalaVersion),
      s"""sbt/sbt#4955!
           |sbt launcher is unable to detect the Scala version for ${id.groupID}:${id.name};
           |this likely indicates an incomplete artifact resolution and/or corrupt boot cache (~/.sbt/boot/).
           |the following is the full classpath that was retrieved for ${id.groupID}:${id.name}:
           |""".stripMargin + retrievedApp.fullClasspath.toList
        .map(x => " * " + x.getAbsolutePath)
        .mkString(System.lineSeparator)
    )
    val scalaProvider = getScala(scalaVersion, "(for " + id.name + ")")
    val resolvedId = resolveId(retrievedApp.resolvedAppVersion, id)

    val (missing, appProvider) = checkedAppProvider(resolvedId, retrievedApp, scalaProvider)
    if (missing.isEmpty)
      appProvider
    else if (retrievedApp.fresh)
      app.retrieveCorrupt(missing)
    else
      getAppProvider0(resolvedId, explicitScalaVersion, true)
  }
  def scalaHome(scalaOrg: String, scalaVersion: Option[String]): File =
    new File(bootDirectory, baseDirectoryName(scalaOrg, scalaVersion))
  def appHome(id: xsbti.ApplicationID, scalaVersion: Option[String]): File =
    appDirectory(scalaHome(ScalaOrg, scalaVersion), id)
  def checkedAppProvider(
      id: xsbti.ApplicationID,
      module: RetrievedModule,
      scalaProvider: xsbti.ScalaProvider
  ): (Iterable[String], xsbti.AppProvider) = {
    val p = appProvider(id, module, scalaProvider, appHome(id, Some(scalaProvider.version)))
    val missing = getMissing(p.loader, id.mainClass :: Nil)
    (missing, p)
  }
  private[this] def locked[T](c: Callable[T]): T = Locks(orNull(updateLockFile), c)
  def getScalaProvider(
      scalaOrg: String,
      scalaVersion: String,
      reason: String,
      classLoader: ClassLoader
  ): xsbti.ScalaProvider =
    locked(new Callable[xsbti.ScalaProvider] {
      def call = getScalaProvider0(scalaOrg, scalaVersion, reason, classLoader)
    })

  private[this] final def getScalaProvider0(
      scalaOrg: String,
      scalaVersion: String,
      reason: String,
      classLoader: ClassLoader
  ) = {
    val scalaM = scalaModule(scalaOrg, scalaVersion)
    val (scalaHome, lib) = scalaDirs(scalaM, scalaOrg, scalaVersion)
    val baseDirs = lib :: Nil
    def testLoadScalaClasses =
      if (scalaVersion.startsWith("2.")) TestLoadScala2Classes
      else TestLoadScala3Classes
    def provider(retrieved: RetrievedModule): xsbti.ScalaProvider = {
      val p = scalaProvider(scalaVersion, retrieved, classLoader, lib)
      checkLoader(p.loader, retrieved.definition, testLoadScalaClasses, p)
    }
    existing(scalaM, scalaOrg, Some(scalaVersion), _ => baseDirs) flatMap { mod =>
      try Some(provider(mod))
      catch { case e: Exception => None }
    } getOrElse {
      val (_, scalaVersion) = update(scalaM, reason)
      provider(new RetrievedModule(true, scalaM, scalaVersion, baseDirs))
    }
  }

  def existing(
      module: ModuleDefinition,
      scalaOrg: String,
      explicitScalaVersion: Option[String],
      baseDirs: File => List[File]
  ): Option[RetrievedModule] = {
    val filter = new java.io.FileFilter {
      val explicitName = explicitScalaVersion.map(sv => baseDirectoryName(scalaOrg, Some(sv)))
      def accept(file: File) = file.isDirectory && explicitName.forall(_ == file.getName)
    }
    val retrieved = wrapNull(bootDirectory.listFiles(filter)) flatMap { scalaDir =>
      val appDir = directory(scalaDir, module.target)
      if (appDir.exists)
        new RetrievedModule(false, module, extractScalaVersion(scalaDir), baseDirs(scalaDir)) :: Nil
      else
        Nil
    }
    retrieved.headOption
  }
  def directory(scalaDir: File, target: UpdateTarget): File = target match {
    case _: UpdateScala => scalaDir
    case ua: UpdateApp  => appDirectory(scalaDir, ua.id.toID)
  }
  def appBaseDirs(scalaHome: File, id: xsbti.ApplicationID): List[File] = {
    val appHome = appDirectory(scalaHome, id)
    val components = componentProvider(appHome)
    appHome :: id.mainComponents.map(components.componentLocation).toList
  }
  def appDirectory(base: File, id: xsbti.ApplicationID): File =
    new File(base, appDirectoryName(id, File.separator))

  def scalaDirs(module: ModuleDefinition, scalaOrg: String, scalaVersion: String): (File, File) = {
    val scalaHome = new File(bootDirectory, baseDirectoryName(scalaOrg, Some(scalaVersion)))
    val libDirectory = new File(scalaHome, ScalaDirectoryName)
    (scalaHome, libDirectory)
  }

  def appProvider(
      appID: xsbti.ApplicationID,
      app: RetrievedModule,
      scalaProvider0: xsbti.ScalaProvider,
      appHome: File
  ): xsbti.AppProvider =
    new xsbti.AppProvider {
      import Launch.AppMainClass
      val scalaProvider = scalaProvider0
      val id = appID
      def mainClasspath = app.fullClasspath
      lazy val loader = app.createLoader(scalaProvider.loader)
      // TODO - For some reason we can't call this from vanilla scala.  We get a
      // no such method exception UNLESS we're in the same project.
      lazy val entryPoint: Class[T] forSome { type T } = {
        val c = Class.forName(id.mainClass, true, loader)
        if (ServerApplication.isServerApplication(c)) c
        else if (classOf[xsbti.AppMain].isAssignableFrom(c)) c
        else if (PlainApplication.isPlainApplication(c)) c
        else
          sys.error(
            s"${c} is not an instance of xsbti.AppMain, xsbti.ServerMain nor does it have one of these static methods:\n" +
              " * void main(String[] args)\n * int main(String[] args)\n * xsbti.Exit main(String[] args)\n"
          )
      }
      // Deprecated API.  Remove when we can.
      def mainClass: Class[T] forSome { type T <: xsbti.AppMain } =
        entryPoint.asSubclass(AppMainClass)
      def newMain(): xsbti.AppMain = {
        if (ServerApplication.isServerApplication(entryPoint)) ServerApplication(this)
        else if (AppMainClass.isAssignableFrom(entryPoint)) mainClass.newInstance
        else if (PlainApplication.isPlainApplication(entryPoint)) PlainApplication(entryPoint)
        else
          throw new IncompatibleClassChangeError(
            s"main class ${entryPoint.getName} is not an instance of xsbti.AppMain, xsbti.ServerMain nor does it have a valid `main` method."
          )
      }
      lazy val components = componentProvider(appHome)
    }
  def componentProvider(appHome: File) = new ComponentProvider(appHome, lockBoot)

  def scalaProvider(
      scalaVersion: String,
      module: RetrievedModule,
      parentLoader: ClassLoader,
      scalaLibDir: File
  ): xsbti.ScalaProvider = new xsbti.ExtendedScalaProvider {
    def launcher = Launch.this
    def version = scalaVersion

    private object LoaderInit {
      val (library, other) = module.fullClasspath.partition(_.getName.startsWith("scala-library"))
      val libraryLoader = new LibraryClassLoader(toURLs(library), parentLoader, scalaVersion)
      val fullLoader = new URLClassLoader(toURLs(other), libraryLoader)
    }

    override def loaderLibraryOnly(): ClassLoader = LoaderInit.libraryLoader
    override def loader(): ClassLoader = LoaderInit.fullLoader
    def compilerJar = new File(scalaLibDir, CompilerModuleName + ".jar")
    def libraryJar = new File(scalaLibDir, LibraryModuleName + ".jar")
    def jars = module.fullClasspath

    def app(id: xsbti.ApplicationID) = Launch.this.app(id, Some(scalaVersion))
  }

  def appModule(
      id: xsbti.ApplicationID,
      scalaVersion: Option[String],
      getClassifiers: Boolean,
      tpe: String
  ): ModuleDefinition = new ModuleDefinition(
    configuration = makeConfiguration(ScalaOrg, scalaVersion),
    target =
      new UpdateApp(Application(id), if (getClassifiers) Value.get(classifiers.app) else Nil, tpe),
    failLabel = id.name + " " + id.version,
    extraClasspath = id.classpathExtra
  )
  def scalaModule(org: String, version: String): ModuleDefinition = new ModuleDefinition(
    configuration = makeConfiguration(org, Some(version)),
    target = new UpdateScala(Value.get(classifiers.forScala)),
    failLabel = "Scala " + version,
    extraClasspath = array()
  )

  /** Returns the resolved appVersion (if this was an App), as well as the scalaVersion. */
  def update(mm: ModuleDefinition, reason: String): (Option[String], Option[String]) = {
    val result = (new Update(mm.configuration))(mm.target, reason)
    if (result.success) result.appVersion -> result.scalaVersion else mm.retrieveFailed
  }
}
object Launcher {
  def apply(bootDirectory: File, repositories: List[Repository.Repository]): xsbti.Launcher =
    apply(
      bootDirectory,
      IvyOptions(
        None,
        Classifiers(Nil, Nil),
        repositories,
        BootConfiguration.DefaultChecksums,
        false
      )
    )
  def apply(bootDirectory: File, ivyOptions: IvyOptions): xsbti.Launcher =
    apply(bootDirectory, ivyOptions, GetLocks.find)
  def apply(bootDirectory: File, ivyOptions: IvyOptions, locks: xsbti.GlobalLock): xsbti.Launcher =
    new Launch(bootDirectory, true, ivyOptions) {
      override def globalLock = locks
    }
  def apply(explicit: LaunchConfiguration): xsbti.Launcher =
    new Launch(explicit.boot.directory, explicit.boot.lock, explicit.ivyConfiguration)
  def defaultAppProvider(baseDirectory: File): xsbti.AppProvider =
    getAppProvider(baseDirectory, Configuration.configurationOnClasspath)
  def getAppProvider(baseDirectory: File, configLocation: URL): xsbti.AppProvider = {
    val parsed = ResolvePaths(Configuration.parse(configLocation, baseDirectory), baseDirectory)
    Initialize.process(parsed.boot.properties, parsed.appProperties, Initialize.selectQuick)
    val config = ResolveValues(parsed)
    val launcher = apply(config)
    launcher.app(config.app.toID, orNull(config.getScalaVersion))
  }
}

// Do not change this without testing sbt 1.3.13
// https://github.com/sbt/sbt/blob/v1.3.13/main/src/main/scala/sbt/internal/XMainConfiguration.scala#L32-L33
// https://github.com/sbt/sbt/issues/6405
private[boot] class TestInterfaceLoader(jar: File, parent: ClassLoader, topLoader: ClassLoader)
    extends URLClassLoader(Array(jar.toURI.toURL), topLoader)

class ComponentProvider(baseDirectory: File, lockBoot: Boolean) extends xsbti.ComponentProvider {
  def componentLocation(id: String): File = new File(baseDirectory, id)
  def component(id: String) = wrapNull(componentLocation(id).listFiles).filter(_.isFile)
  def defineComponent(id: String, files: Array[File]) = {
    val location = componentLocation(id)
    if (location.exists)
      throw new BootException(
        "cannot redefine component.  ID: " + id + ", files: " + files.mkString(",")
      )
    else
      Copy(files.toList, location)
    ()
  }
  def addToComponent(id: String, files: Array[File]): Boolean =
    Copy(files.toList, componentLocation(id))
  def lockFile =
    if (lockBoot) ComponentProvider.lockFile(baseDirectory) else null // null for the Java interface
}
object ComponentProvider {
  def lockFile(baseDirectory: File) = {
    baseDirectory.mkdirs()
    new File(baseDirectory, "sbt.components.lock")
  }
}
