import com.typesafe.sbt.JavaVersionCheckPlugin.autoImport._
import sbt.Keys._
import sbt._

object Release extends Build {
  lazy val remoteBase = SettingKey[String]("remote-base")
  lazy val remoteID = SettingKey[String]("remote-id")
  lazy val launcherRemotePath = SettingKey[String]("launcher-remote-path")
  lazy val deployLauncher = TaskKey[Unit]("deploy-launcher", "Upload the launcher to its traditional location for compatibility with existing scripts.")
  lazy val checkCredentials = TaskKey[Unit]("checkCredentials", "Checks to ensure credentials for this user exists.")

  val PublishRepoHost = "private-repo.typesafe.com"

  def settings(nonRoots: => Seq[ProjectReference], launcher: TaskKey[File]): Seq[Setting[_]] =
    releaseSettings(nonRoots, launcher)

  // Add credentials if they exist.
  def lameCredentialSettings: Seq[Setting[_]] =
    if (CredentialsFile.exists) Seq(credentials in ThisBuild += Credentials(CredentialsFile))
    else Nil
  def releaseSettings(nonRoots: => Seq[ProjectReference], launcher: TaskKey[File]): Seq[Setting[_]] = Seq(
  // TODO - Fix release settings
    //publishTo in ThisBuild <<= publishResolver,
    //remoteID <<= "typesafe-mvn-releases",
    //remoteBase <<= publishStatus("https://" + PublishRepoHost + "/typesafe/ivy-" + _),
    //launcherRemotePath <<= (organization, version, moduleName) { (org, v, n) => List(org, n, v, n + ".jar").mkString("/") },
    //publish <<= Seq(publish, Release.deployLauncher).dependOn,
    //deployLauncher <<= deployLauncher(launcher),
    checkCredentials := {
      // Note - This will either issue a failure or succeed.
      getCredentials(credentials.value, streams.value.log)
    }
  ) ++ lameCredentialSettings ++ javaVersionCheckSettings

  def snapshotPattern(version: String) = Resolver.localBasePattern.replaceAll("""\[revision\]""", version)
  def publishResolver: Def.Initialize[Option[Resolver]] = (remoteID, remoteBase) { (id, base) =>
    Some(Resolver.url("publish-" + id, url(base))(Resolver.ivyStylePatterns))
  }

  // Hackery so we use the same old credentials as the sbt build, if necessary.
  lazy val CredentialsFile: File = Path.userHome / ".ivy2" / ".typesafe-credentials"
  def getCredentials(cs: Seq[Credentials], log: Logger): (String, String) =
    {
      Credentials.forHost(cs, PublishRepoHost) match {
        case Some(creds) => (creds.userName, creds.passwd)
        case None        => sys.error("No credentials defined for " + PublishRepoHost)
      }
    }

  def javaVersionCheckSettings = Seq(
    javaVersionPrefix in javaVersionCheck := Some("1.6")
  )
}
