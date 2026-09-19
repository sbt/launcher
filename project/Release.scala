import com.typesafe.sbt.JavaVersionCheckPlugin.autoImport.*
import sbt.*

object Release {
  def settings: Seq[Setting[?]] = javaVersionCheckSettings

  // Validation for java verison
  def javaVersionCheckSettings = Seq(
    javaVersionCheck / javaVersionPrefix := Some("11")
  )
}
