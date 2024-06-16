/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

import java.nio.file.{ Path, Paths }

// The entry point to the launcher
object Boot {
  lazy val defaultGlobalBase: Path = Paths.get(sys.props("user.home"), ".sbt", "1.0")
  lazy val globalBase = sys.props.get("sbt.global.base").getOrElse(defaultGlobalBase.toString)

  def main(args: Array[String]): Unit = {
    standBy()
    val config = parseArgs(args)
    // If we havne't exited, we set up some hooks and launch
    System.clearProperty("scala.home") // avoid errors from mixing Scala versions in the same JVM
    System.setProperty("jline.shutdownhook", "false") // shutdown hooks cause class loader leaks
    System.setProperty("jline.esc.timeout", "0") // starts up a thread otherwise
    CheckProxy()
    run(config)
  }

  def standBy(): Unit = {
    import scala.concurrent.duration.Duration
    val x = System.getProperty("sbt.launcher.standby")
    if (x == null) ()
    else {
      val sec = Duration(x).toSeconds
      if (sec >= 1) {
        (sec to 1 by -1) foreach { i =>
          Console.err.println(s"[info] [launcher] standing by: $i")
          Thread.sleep(1000)
        }
      }
    }
  }

  def parseArgs(args: Array[String]): LauncherArguments = {
    @annotation.tailrec
    def parse(
        args: List[String],
        isLocate: Boolean,
        isExportRt: Boolean,
        remaining: List[String]
    ): LauncherArguments =
      args match {
        case "--launcher-version" :: rest =>
          Console.err.println(
            "sbt launcher version " + Package.getPackage("xsbt.boot").getImplementationVersion
          )
          exit(0)
        case "--rt-ext-dir" :: rest =>
          var v = sys.props("java.vendor") + "_" + sys.props("java.version")
          v = v.replaceAll("\\W", "_").toLowerCase
          /*
           * The launch script greps for output starting with "java9-rt-ext-" so changing this
           * string will require changing the grep command in sbt-launch-lib.bash.
           */
          val rtExtDir = Paths.get(globalBase, "java9-rt-ext-" + v)
          Console.out.println(rtExtDir.toString)
          exit(0)
        case "--locate" :: rest    => parse(rest, true, isExportRt, remaining)
        case "--export-rt" :: rest => parse(rest, isLocate, true, remaining)
        case next :: rest          => parse(rest, isLocate, isExportRt, next :: remaining)
        case Nil                   => new LauncherArguments(remaining.reverse, isLocate, isExportRt)
      }
    parse(args.toList, false, false, Nil)
  }

  // this arrangement is because Scala does not always properly optimize away
  // the tail recursion in a catch statement
  final def run(args: LauncherArguments): Unit = runImpl(args) match {
    case Some(newArgs) => run(newArgs)
    case None          => ()
  }
  private def runImpl(args: LauncherArguments): Option[LauncherArguments] =
    try Launch(args) map exit
    catch {
      case b: BootException           => errorAndExit(b.toString)
      case r: xsbti.RetrieveException => errorAndExit(r.getMessage)
      case r: xsbti.FullReload        => Some(new LauncherArguments(r.arguments.toList, false, false))
      case e: Throwable =>
        e.printStackTrace
        errorAndExit(Pre.prefixError(e.toString))
    }

  private def errorAndExit(msg: String): Nothing = {
    msg.linesIterator.toList foreach { line =>
      Console.err.println("[error] [launcher] " + line)
    }
    exit(1)
  }

  private def exit(code: Int): Nothing =
    sys.exit(code)
}
