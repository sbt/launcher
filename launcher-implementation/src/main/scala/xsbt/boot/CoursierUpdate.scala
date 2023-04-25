package xsbt.boot

import Pre._
import coursier._
import coursier.cache.{ CacheDefaults, FileCache }
import coursier.core.{ Publication, Repository }
import coursier.credentials.DirectCredentials
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.params.ResolutionParams
import java.io.{ File, FileWriter, PrintWriter }
import java.nio.file.{ Files, StandardCopyOption, Paths }
import java.util.Properties
import java.util.regex.Pattern
import BootConfiguration._

class CousierUpdate(config: UpdateConfiguration) {
  import config.{
    bootDirectory,
    getScalaVersion,
    repositories,
    resolutionCacheBase,
    scalaVersion,
    scalaOrg,
  }

  private def logFile = new File(bootDirectory, UpdateLogName)
  private val logWriter = new PrintWriter(new FileWriter(logFile))

  private def defaultCacheLocation: File = {
    def absoluteFile(path: String): File = new File(path).getAbsoluteFile()
    def windowsCacheDirectory: File = {
      // Per discussion in https://github.com/dirs-dev/directories-jvm/issues/43,
      // LOCALAPPDATA environment variable may NOT represent the one-true
      // Known Folders API (https://docs.microsoft.com/en-us/windows/win32/shell/knownfolderid)
      // in case the user happened to have set the LOCALAPPDATA environmental variable.
      // Given that there's no reliable way of accessing this API from JVM, I think it's actually
      // better to use the LOCALAPPDATA as the first place to look.
      // When it is not found, it will fall back to $HOME/AppData/Local.
      // For the purpose of picking the Coursier cache directory, it's better to be
      // fast, reliable, and predictable rather than strict adherence to Microsoft.
      val base =
        sys.env
          .get("LOCALAPPDATA")
          .map(absoluteFile)
          .getOrElse(new File(new File(absoluteFile(sys.props("user.home")), "AppData"), "Local"))
      new File(new File(new File(base, "Coursier"), "Cache"), "v1")
    }
    sys.props
      .get("sbt.coursier.home")
      .map(home => new File(absoluteFile(home), "cache"))
      .orElse(sys.env.get("COURSIER_CACHE").map(absoluteFile))
      .orElse(sys.props.get("coursier.cache").map(absoluteFile)) match {
      case Some(dir) => dir
      case _ =>
        if (isWindows) windowsCacheDirectory
        else CacheDefaults.location
    }
  }
  private lazy val coursierCache = {
    import coursier.util.Task
    val credentials = bootCredentials
    val cache = credentials.foldLeft(FileCache(defaultCacheLocation)(Task.sync)) {
      _.addCredentials(_)
    }
    cache
  }
  private lazy val coursierRepos: Seq[Repository] =
    if (repositories.isEmpty) Resolve.defaultRepositories
    else Nil

  def apply(target: UpdateTarget, reason: String): UpdateResult = {
    try {
      update(target, reason)
    } catch {
      case e: Throwable =>
        e.printStackTrace(logWriter)
        log("[error] [launcher] " + e.toString)
        new UpdateResult(false, None, None)
    } finally {
      delete(resolutionCacheBase)
    }
  }

  private def update(target: UpdateTarget, reason: String): UpdateResult = {
    val deps = target match {
      case u: UpdateScala =>
        val scalaVersion = getScalaVersion
        val scalaOrgString = if (scalaOrg != ScalaOrg) scalaOrg + " " else ""
        Console.err.println(
          s"[info] [launcher] getting ${scalaOrgString}Scala $scalaVersion ${reason}..."
        )
        scalaVersion match {
          case sv if sv.startsWith("2.") =>
            withPublication(
              Dependency(
                Module(Organization(scalaOrg), ModuleName(CompilerModuleName)),
                scalaVersion
              ),
              u.classifiers
            ) :::
              withPublication(
                Dependency(
                  Module(Organization(scalaOrg), ModuleName(LibraryModuleName)),
                  scalaVersion
                ),
                u.classifiers
              )
          case sv if sv.startsWith("3.") =>
            withPublication(
              Dependency(
                Module(Organization(scalaOrg), ModuleName(Compiler3ModuleName)),
                scalaVersion
              ),
              u.classifiers
            ) :::
              withPublication(
                Dependency(
                  Module(Organization(scalaOrg), ModuleName(Library3ModuleName)),
                  scalaVersion
                ),
                u.classifiers
              )
          case _ =>
            sys.error("unsupported Scala version " + scalaVersion)
        }
      case u: UpdateApp =>
        val app = u.id
        val resolvedName = (app.crossVersioned, scalaVersion) match {
          case (xsbti.CrossValue.Full, Some(sv)) => app.getName + "_" + sv
          case (xsbti.CrossValue.Binary, Some(sv)) =>
            app.getName + "_" + CrossVersionUtil.binaryScalaVersion(sv)
          case _ => app.getName
        }
        Console.err.println(
          s"[info] [launcher] getting ${app.groupID} $resolvedName ${app.getVersion} $reason (this may take some time)..."
        )
        withPublication(
          Dependency(
            Module(Organization(app.groupID), ModuleName(resolvedName)),
            app.getVersion
          ),
          u.classifiers
        ) :::
          (scalaVersion match {
            case Some(sv) if sv != "auto" =>
              withPublication(
                Dependency(
                  Module(Organization(scalaOrg), ModuleName(LibraryModuleName)),
                  sv
                ),
                u.classifiers
              )
            case _ => Nil
          })
    }
    update(target, deps)
  }

  private def detectScalaVersion(dependencySet: Set[Dependency]): Option[String] = {
    def detectScalaVersion3: Option[String] =
      (dependencySet collect {
        case d: Dependency
            if d.module == Module(Organization(scalaOrg), ModuleName(Library3ModuleName)) =>
          d.version
      }).headOption
    def detectScalaVersion2: Option[String] =
      (dependencySet collect {
        case d: Dependency
            if d.module == Module(Organization(scalaOrg), ModuleName(LibraryModuleName)) =>
          d.version
      }).headOption
    detectScalaVersion3.orElse(detectScalaVersion2)
  }

  /** Runs the resolve and retrieve for the given moduleID, which has had its dependencies added already. */
  private def update(
      target: UpdateTarget,
      deps: List[Dependency]
  ): UpdateResult = {
    val repos = config.repositories.map(toCoursierRepository)
    val params = scalaVersion match {
      case Some(sv) if sv != "auto" =>
        ResolutionParams()
          .withScalaVersion(sv)
          .withForceScalaVersion(true)
      case _ =>
        detectScalaVersion(deps.toSet) match {
          case Some(sv) =>
            ResolutionParams()
              .withScalaVersion(sv)
              .withForceScalaVersion(true)
          case _ =>
            ResolutionParams()
        }
    }
    val r: Resolution = Resolve()
      .withCache(coursierCache)
      .addDependencies(deps: _*)
      .withRepositories(repos)
      .withResolutionParams(params)
      .run()
    val actualScalaVersion = detectScalaVersion(r.dependencySet.set)
    val retrieveDir = target match {
      case u: UpdateScala =>
        new File(new File(bootDirectory, baseDirectoryName(scalaOrg, scalaVersion)), "lib")
      case u: UpdateApp =>
        new File(
          new File(bootDirectory, baseDirectoryName(scalaOrg, actualScalaVersion)),
          appDirectoryName(u.id.toID, File.separator)
        )
    }
    val isScala = target match {
      case _: UpdateScala => true
      case _: UpdateApp   => false
    }
    val depVersion: Option[String] = target match {
      case u: UpdateScala => scalaVersion
      case u: UpdateApp   => Some(Value.get(u.id.version))
    }
    if (!retrieveDir.exists) {
      Files.createDirectories(retrieveDir.toPath)
    }
    val downloadedJars = Fetch()
      .withCache(coursierCache)
      .addDependencies(deps: _*)
      .withRepositories(repos)
      .withResolutionParams(params)
      .run()
    downloadedJars foreach { downloaded =>
      val t =
        if (isScala) {
          val name = downloaded.getName match {
            case n if n.startsWith("scala-compiler") => "scala-compiler.jar"
            case n if n.startsWith("scala-library")  => "scala-library.jar"
            case n if n.startsWith("scala-reflect")  => "scala-reflect.jar"
            case n                                   => n
          }
          new File(retrieveDir, name)
        } else {
          val name = downloaded.getName match {
            // https://github.com/sbt/sbt/issues/6432
            // sbt expects test-interface JAR to be called test-interface-1.0.jar with
            // version number, but sometimes it doesn't have it.
            case "test-interface.jar" =>
              if (Pattern.matches("""[0-9.]+""", downloaded.getParentFile.getName))
                "test-interface-" + downloaded.getParentFile.getName + ".jar"
              else "test-interface-0.0.jar"
            case n => n
          }
          new File(retrieveDir, name)
        }
      val isSkip =
        isScala && (downloaded.getName match {
          case n if n.startsWith("compiler-interface") => true
          case n if n.startsWith("util-interface")     => true
          case _                                       => false
        })
      if (isSkip) ()
      else {
        Files.copy(downloaded.toPath, t.toPath, StandardCopyOption.REPLACE_EXISTING)
        ()
      }
    }
    new UpdateResult(true, actualScalaVersion, depVersion)
  }

  def withPublication(d: Dependency, classifiers: List[String]): List[Dependency] =
    if (classifiers.isEmpty) List(d)
    else classifiers.map(c => d.withPublication(Publication.empty.withClassifier(Classifier(c))))

  def bootCredentials = {
    val optionProps =
      Option(System.getProperty("sbt.boot.credentials")) orElse
        Option(System.getenv("SBT_CREDENTIALS")) map (
          path => Pre.readProperties(new File(substituteTilde(path)))
      )
    def extractCredentials(
        keys: (String, String, String, String)
    )(props: Properties): Option[DirectCredentials] = {
      val List(realm, host, user, password) =
        keys.productIterator.map(key => props.getProperty(key.toString)).toList
      if (host != null && user != null && password != null)
        Some(
          DirectCredentials()
            .withHost(host)
            .withUsername(user)
            .withPassword(password)
            .withRealm(Option(realm).filter(_.nonEmpty))
            .withHttpsOnly(false)
            .withMatchHost(true)
        )
      else None
    }
    (optionProps match {
      case Some(props) => extractCredentials(("realm", "host", "user", "password"))(props)
      case None        => None
    }).toList :::
      (extractCredentials(
        ("sbt.boot.realm", "sbt.boot.host", "sbt.boot.user", "sbt.boot.password")
      )(
        System.getProperties
      )).toList
  }

  def toCoursierRepository(repo: xsbti.Repository): Repository = {
    import xsbti.Predefined._
    repo match {
      case m: xsbti.MavenRepository =>
        mavenRepository(m.url.toString)
      case i: xsbti.IvyRepository =>
        ivyRepository(
          i.id,
          i.url.toString,
          i.ivyPattern,
          i.artifactPattern,
          i.mavenCompatible,
          i.descriptorOptional,
          i.skipConsistencyCheck,
          i.allowInsecureProtocol
        )
      case p: xsbti.PredefinedRepository =>
        p.id match {
          case Local =>
            localRepository
          case MavenLocal =>
            val localDir = new File(new File(new File(sys.props("user.home")), ".m2"), "repository")
            mavenRepository(localDir.toPath.toUri.toString)
          case MavenCentral =>
            Repositories.central
          case SonatypeOSSReleases =>
            Repositories.sonatype("releases")
          case SonatypeOSSSnapshots =>
            Repositories.sonatype("snapshots")
          case Jcenter =>
            Repositories.jcenter
        }
    }
  }

  private def mavenRepository(root0: String): MavenRepository = {
    val root = if (root0.endsWith("/")) root0 else root0 + "/"
    MavenRepository(root)
  }

  /** Uses the pattern defined in BuildConfiguration to download sbt from Google code.*/
  private def ivyRepository(
      id: String,
      base: String,
      ivyPattern: String,
      artifactPattern: String,
      mavenCompatible: Boolean,
      descriptorOptional: Boolean,
      skipConsistencyCheck: Boolean,
      allowInsecureProtocol: Boolean
  ): IvyRepository =
    IvyRepository
      .parse(
        base + artifactPattern,
        Some(base + ivyPattern),
      )
      .right
      .get

  private def localRepository: IvyRepository = {
    val localDir = new File(new File(new File(sys.props("user.home")), ".ivy2"), "local")
    val root0 = localDir.toPath.toUri.toASCIIString
    val root = if (root0.endsWith("/")) root0 else root0 + "/"
    IvyRepository
      .parse(
        root + LocalArtifactPattern,
        Some(root + LocalIvyPattern)
      )
      .right
      .get
  }

  private def pathToUriString(path: String): String = {
    val stopAtIdx = path.indexWhere(c => c == '[' || c == '$' || c == '(')
    if (stopAtIdx > 0) {
      val (pathPart, patternPart) = path.splitAt(stopAtIdx)
      Paths.get(pathPart).toUri.toASCIIString + patternPart
    } else if (stopAtIdx == 0)
      "file://" + path
    else
      Paths.get(path).toUri.toASCIIString
  }

  /** Logs the given message to a file and to the console. */
  private def log(msg: String) = {
    try {
      logWriter.println(msg)
    } catch {
      case e: Exception =>
        Console.err.println("[error] [launcher] error writing to update log file: " + e.toString)
    }
    Console.err.println(msg)
  }
}
