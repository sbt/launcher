/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

// The entry point to the launcher
object Boot {
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
    sys.props.get("sbt.launcher.standby") foreach { s =>
      val sec = Duration(s).toSeconds
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
    def parse(args: List[String], isLocate: Boolean, remaining: List[String]): LauncherArguments =
      args match {
        case "--launcher-version" :: rest =>
          Console.err.println(
            "sbt launcher version " + Package.getPackage("xsbt.boot").getImplementationVersion
          )
          exit(0)
        case "--locate" :: rest => parse(rest, true, remaining)
        case next :: rest       => parse(rest, isLocate, next :: remaining)
        case Nil                => new LauncherArguments(remaining.reverse, isLocate)
      }
    parse(args.toList, false, Nil)
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
      case r: xsbti.FullReload        => Some(new LauncherArguments(r.arguments.toList, false))
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
    System.exit(code).asInstanceOf[Nothing]
}
