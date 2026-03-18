/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

import Pre.*
import java.io.File
import java.net.URI
import scala.collection.immutable.List

object Find:
  def apply(config: LaunchConfiguration, currentDirectory: File) =
    (new Find(config))(currentDirectory)
class Find(config: LaunchConfiguration):
  import config.boot.search
  def apply(currentDirectory: File) =
    val current = currentDirectory.getCanonicalFile
    assert(current.isDirectory)

    lazy val fromRoot = path(current, Nil).filter(hasProject).map(_.getCanonicalFile)
    val found: Option[File] =
      search.tpe match
        case Search.RootFirst => fromRoot.headOption
        case Search.Nearest   => fromRoot.lastOption
        case Search.Only      =>
          if hasProject(current) then Some(current)
          else
            fromRoot match
              case Nil         => Some(current)
              case head :: Nil => Some(head)
              case _           =>
                Console.err.println(
                  "[error] [launcher] search method is 'only' and multiple ancestor directories match:\n\t" + fromRoot
                    .mkString("\n\t")
                )
                Boot.exit(1)
                None
        case _ => Some(current)
    val baseDirectory = orElse(found, current)
    System.setProperty("user.dir", baseDirectory.getAbsolutePath)
    (ResolvePaths(config, baseDirectory), baseDirectory)
  private def hasProject(f: File) =
    f.isDirectory && search.paths.forall(p => ResolvePaths(f, p).exists)
  private def path(f: File, acc: List[File]): List[File] =
    if f eq null then acc else path(f.getParentFile, f :: acc)
object ResolvePaths:
  def apply(config: LaunchConfiguration, baseDirectory: File): LaunchConfiguration =
    config.map(f => apply(baseDirectory, f))
  def apply(baseDirectory: File, f: File): File =
    if f.isAbsolute then f
    else
      assert(
        baseDirectory.isDirectory
      ) // if base directory is not a directory, URI.resolve will not work properly
      val uri = new URI(null, null, f.getPath, null)
      new File(baseDirectory.toURI.resolve(uri))
