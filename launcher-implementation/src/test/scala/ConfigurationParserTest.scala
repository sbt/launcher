/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

import java.io.File
import java.net.URI
import java.nio.file.Files

object ConfigurationParserTest extends verify.BasicTestSuite:
  test("repository override activation combines the configured value and file existence") {
    val directory = Files.createTempDirectory("launcher-repository-override-test").toFile
    val repositoryConfig = new File(directory, "repositories")
    val repositoryOverride = new File(directory, "repositories_force")
    Files.write(repositoryConfig.toPath, "[repositories]\n  local\n".getBytes("UTF-8"))

    try
      val cases = List(
        (false, false, false, repositoryConfig),
        (true, false, true, repositoryConfig),
        (false, true, true, repositoryOverride),
        (true, true, true, repositoryOverride)
      )

      cases.foreach {
        case (configuredOverride, overrideFileExists, expectedOverride, expectedFile) =>
          if overrideFileExists then
            Files.write(
              repositoryOverride.toPath,
              "[repositories]\n  maven-central\n".getBytes("UTF-8")
            )
          else Files.deleteIfExists(repositoryOverride.toPath)

          val ivy =
            ListMap[String, Option[String]](
              "override-build-repos" -> Option(configuredOverride.toString),
              "repository-config" -> Option(repositoryConfig.getAbsolutePath),
              "repository-override" -> Option(repositoryOverride.getAbsolutePath)
            ).default(_ => None)
          val (_, _, actualOverride, actualFile) = (new ConfigurationParser).getIvy(ivy)

          assert(actualOverride == expectedOverride)
          assert(actualFile.contains(expectedFile))
      }
    finally
      Files.deleteIfExists(repositoryOverride.toPath)
      Files.deleteIfExists(repositoryConfig.toPath)
      Files.deleteIfExists(directory.toPath)
  }

  test("Configuration parser should correct parse bootOnly") {
    repoFileContains(
      """|[repositories]
                                          |  local: bootOnly""".stripMargin,
      Repository.Predefined("local", true)
    )

    repoFileContains(
      """|[repositories]
                                          |  local""".stripMargin,
      Repository.Predefined("local", false)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org""".stripMargin,
      Repository.Maven("id", new URI("https://repo1.maven.org").toURL, false)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, bootOnly""".stripMargin,
      Repository.Maven("id", new URI("https://repo1.maven.org").toURL, true)
    )

    repoFileContains(
      """|[repositories]
         |  id: http://repo1.maven.org, bootOnly, allowInsecureProtocol""".stripMargin,
      Repository.Maven("id", new URI("http://repo1.maven.org").toURL, true, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath]""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[orgPath]", false, false)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], mavenCompatible""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[orgPath]", true, false)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], mavenCompatible, bootOnly""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[orgPath]", true, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], bootOnly, mavenCompatible""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[orgPath]", true, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], bootOnly""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[orgPath]", false, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath]""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[artPath]", false, false)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional""".stripMargin,
      Repository.Ivy(
        "id",
        new URI("https://repo1.maven.org").toURL,
        "[orgPath]",
        "[artPath]",
        false,
        false,
        true,
        false
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional, skipConsistencyCheck""".stripMargin,
      Repository.Ivy(
        "id",
        new URI("https://repo1.maven.org").toURL,
        "[orgPath]",
        "[artPath]",
        false,
        false,
        true,
        true
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], skipConsistencyCheck, descriptorOptional""".stripMargin,
      Repository.Ivy(
        "id",
        new URI("https://repo1.maven.org").toURL,
        "[orgPath]",
        "[artPath]",
        false,
        false,
        true,
        true
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], skipConsistencyCheck, descriptorOptional, mavenCompatible, bootOnly""".stripMargin,
      Repository.Ivy(
        "id",
        new URI("https://repo1.maven.org").toURL,
        "[orgPath]",
        "[artPath]",
        true,
        true,
        true,
        true
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], bootOnly""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[artPath]", false, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], bootOnly, mavenCompatible""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[artPath]", true, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], mavenCompatible, bootOnly""".stripMargin,
      Repository
        .Ivy("id", new URI("https://repo1.maven.org").toURL, "[orgPath]", "[artPath]", true, true)
    )

    repoFileContains(
      """|[repositories]
         |  id: http://repo1.maven.org, [orgPath], [artPath], mavenCompatible, bootOnly, allowInsecureProtocol""".stripMargin,
      Repository.Ivy(
        "id",
        new URI("http://repo1.maven.org").toURL,
        "[orgPath]",
        "[artPath]",
        mavenCompatible = true,
        bootOnly = true,
        allowInsecureProtocol = true
      )
    )
  }

  def repoFileContains(file: String, repo: Repository.Repository) =
    assert(loadRepoFile(file).contains(repo))

  def loadRepoFile(file: String) =
    (new ConfigurationParser).readRepositoriesConfig(file)
