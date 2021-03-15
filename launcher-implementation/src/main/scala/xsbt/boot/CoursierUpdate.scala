package xsbt.boot

import Pre._
import coursier._
import coursier.cache.FileCache
import coursier.core.{ Publication, Repository }
import coursier.credentials.DirectCredentials
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import java.io.{ File, FileWriter, PrintWriter }
import java.nio.file.{ Files, StandardCopyOption, Paths }
import java.util.Properties
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
  private lazy val coursierCache = {
    val credentials = bootCredentials
    val cache = credentials.foldLeft(FileCache()) { _.addCredentials(_) }
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

  /** Runs the resolve and retrieve for the given moduleID, which has had its dependencies added already. */
  private def update(
      target: UpdateTarget,
      deps: List[Dependency]
  ): UpdateResult = {
    val r: Resolution = Resolve()
      .withCache(coursierCache)
      .addDependencies(deps: _*)
      .run()
    val actualScalaVersion =
      (r.dependencySet.set collect {
        case d: Dependency
            if d.module == Module(Organization(scalaOrg), ModuleName(LibraryModuleName)) =>
          d.version
      }).headOption
    val retrieveDir = target match {
      case u: UpdateScala =>
        new File(new File(bootDirectory, baseDirectoryName(scalaOrg, scalaVersion)), "lib")
      case u: UpdateApp =>
        new File(
          new File(bootDirectory, baseDirectoryName(scalaOrg, actualScalaVersion)),
          appDirectoryName(u.id.toID, File.separator)
        )
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
      .run()
    downloadedJars foreach { downloaded =>
      val t = new File(retrieveDir, downloaded.getName)
      println(t.toString)
      Files.copy(downloaded.toPath, t.toPath, StandardCopyOption.REPLACE_EXISTING)
      ()
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
        pathToUriString(base + artifactPattern),
        Some(pathToUriString(base + ivyPattern)),
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
