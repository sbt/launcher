import sbt.*
import sbt.Keys.*

object Deps {
  def lib(m: ModuleID) = libraryDependencies += m
  lazy val sbtIo = "org.scala-sbt" %% "io" % "1.10.5"
  lazy val scalacheck = "org.scalacheck" %% "scalacheck" % "1.19.0"
  lazy val junit = "junit" % "junit" % "4.13.2"
  lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

  val coursierVersion = "2.1.23"
  lazy val coursier = ("io.get-coursier" %% "coursier" % coursierVersion)
    .cross(CrossVersion.for3Use2_13)
    .exclude("org.codehaus.plexus", "plexus-archiver")
    .exclude("org.codehaus.plexus", "plexus-container-default")
    .exclude("org.codehaus.plexus", "plexus-container-default")
}
