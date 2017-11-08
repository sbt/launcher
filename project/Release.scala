import com.typesafe.sbt.JavaVersionCheckPlugin.autoImport._
import sbt.Keys._
import sbt._

import scala.xml.{Elem, NodeSeq}
import scala.xml.transform.{RuleTransformer, RewriteRule}

object Release {
  lazy val remoteBase = SettingKey[String]("remote-base")
  lazy val remoteID = SettingKey[String]("remote-id")
  lazy val launcherRemotePath = SettingKey[String]("launcher-remote-path")
  lazy val deployLauncher = TaskKey[Unit]("deploy-launcher", "Upload the launcher to its traditional location for compatibility with existing scripts.")

  val PublishRepoHost = "private-repo.typesafe.com"

  def settings: Seq[Setting[_]] = Seq(
    // Maven central cannot allow other repos.  We're ok here because the artifacts we
    // we use externally are *optional* dependencies.
    pomIncludeRepository := { x => false },
    homepage := Some(url("http://scala-sbt.org")),
    licenses += "BSD" -> new java.net.URL("http://opensource.org/licenses/BSD-2-Clause"),
    scmInfo := Some(ScmInfo(
      browseUrl = new java.net.URL("http://github.com/sbt/launcher"),
      connection = "scm:git@github.com:sbt/launcher.git"
    )),
    developers := List(
      Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n")),
      Developer("jsuereth", "Josh Suereth", "@jsuereth", url("https://github.com/jsuereth")),
      Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand"))
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
  ) ++ lameCredentialSettings ++ javaVersionCheckSettings

  // Add credentials if they exist.
  def lameCredentialSettings: Seq[Setting[_]] =
    if (CredentialsFile.exists) Seq(credentials in ThisBuild += Credentials(CredentialsFile))
    else Nil

  // Hackery so we use the same old credentials as the sbt build, if necessary.
  lazy val CredentialsFile: File = Path.userHome / ".ivy2" / ".typesafe-credentials"
  def getCredentials(cs: Seq[Credentials], log: Logger): (String, String) =
    {
      Credentials.forHost(cs, PublishRepoHost) match {
        case Some(creds) => (creds.userName, creds.passwd)
        case None        => sys.error("No credentials defined for " + PublishRepoHost)
      }
    }

  // Validation for java verison
  def javaVersionCheckSettings = Seq(
    javaVersionPrefix in javaVersionCheck := Some("1.6")
  )
}
