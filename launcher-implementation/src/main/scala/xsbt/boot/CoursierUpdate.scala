package xsbt.boot

import Pre._
import coursier._
import coursier.core.Publication
import java.io.{ File, FileWriter, PrintWriter }
import java.nio.file.{ Files, StandardCopyOption }
import BootConfiguration._

class CousierUpdate(config: UpdateConfiguration) {
  import config.{
    bootDirectory,
    //   checksums,
    getScalaVersion,
    //   ivyHome,
    //   repositories,
    resolutionCacheBase,
    scalaVersion,
    scalaOrg,
  }

  private def logFile = new File(bootDirectory, UpdateLogName)
  private val logWriter = new PrintWriter(new FileWriter(logFile))

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
