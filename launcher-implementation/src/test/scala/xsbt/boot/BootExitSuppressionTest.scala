/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

object BootExitSuppressionTest extends verify.BasicTestSuite:
  private def withExitSuppressed[T](body: => T): T =
    val key = "sbt.boot.exit"
    val prev = sys.props.get(key)
    System.setProperty(key, "false")
    try body
    finally
      prev match
        case None    => System.clearProperty(key)
        case Some(v) => System.setProperty(key, v)

  test("Boot.main does not terminate the JVM for --launcher-version when suppressed") {
    withExitSuppressed {
      Boot.main(Array("--launcher-version"))
      // If sys.exit was called, the test JVM would be terminated.
    }
  }

  test("Boot.main does not terminate the JVM on error path when suppressed") {
    withExitSuppressed {
      // This argument triggers an error path in the launcher (missing/invalid launch configuration).
      // We mainly care that it does not hard-exit the embedding JVM.
      Boot.main(Array("--bad-flag-that-will-error"))
      // If sys.exit was called, the test JVM would be terminated.
    }
  }
