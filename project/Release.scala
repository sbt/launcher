import com.typesafe.sbt.JavaVersionCheckPlugin.autoImport._
import sbt.Keys._
import sbt._

object Release {
  def settings: Seq[Setting[_]] = javaVersionCheckSettings

  // Validation for java verison
  def javaVersionCheckSettings = Seq(
    javaVersionPrefix in javaVersionCheck := Some("1.8")
  )
}
