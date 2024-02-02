package xsbt.boot

import java.net.URL

object ConfigurationParserTest extends verify.BasicTestSuite {
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
      Repository.Maven("id", new URL("https://repo1.maven.org"), false)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, bootOnly""".stripMargin,
      Repository.Maven("id", new URL("https://repo1.maven.org"), true)
    )

    repoFileContains(
      """|[repositories]
         |  id: http://repo1.maven.org, bootOnly, allowInsecureProtocol""".stripMargin,
      Repository.Maven("id", new URL("http://repo1.maven.org"), true, false, true)
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath]""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[orgPath]",
          false,
          false,
          false
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], mavenCompatible""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[orgPath]",
          mavenCompatible = true,
          false,
          false
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], mavenCompatible, bootOnly""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[orgPath]",
          mavenCompatible = true,
          bootOnly = true,
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], bootOnly, mavenCompatible""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[orgPath]",
          mavenCompatible = true,
          bootOnly = true,
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], bootOnly""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[orgPath]",
          mavenCompatible = false,
          bootOnly = true,
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath]""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[artPath]",
          mavenCompatible = false,
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = false,
        descriptorOptional = true,
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional, skipConsistencyCheck""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = false,
        descriptorOptional = true,
        skipConsistencyCheck = true,
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], skipConsistencyCheck, descriptorOptional""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = false,
        descriptorOptional = true,
        skipConsistencyCheck = true,
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], skipConsistencyCheck, descriptorOptional, mavenCompatible, bootOnly""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = true,
        bootOnly = true,
        descriptorOptional = true,
        skipConsistencyCheck = true,
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], bootOnly""".stripMargin,
      Repository
        .Ivy(
          "id",
          new URL("https://repo1.maven.org"),
          "[orgPath]",
          "[artPath]",
          mavenCompatible = false,
          bootOnly = true,
        )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], bootOnly, mavenCompatible""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = true,
        bootOnly = true,
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], mavenCompatible, bootOnly""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = true,
        bootOnly = true,
      )
    )

    repoFileContains(
      """|[repositories]
                                          |  id: https://repo1.maven.org, [orgPath], [artPath], mavenCompatible, bootOnlyZero""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("https://repo1.maven.org"),
        "[orgPath]",
        "[artPath]",
        mavenCompatible = true,
        bootOnlyZero = true,
      )
    )

    repoFileContains(
      """|[repositories]
         |  id: http://repo1.maven.org, [orgPath], [artPath], mavenCompatible, bootOnly, allowInsecureProtocol""".stripMargin,
      Repository.Ivy(
        "id",
        new URL("http://repo1.maven.org"),
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
    (new ConfigurationParser) readRepositoriesConfig file
}
