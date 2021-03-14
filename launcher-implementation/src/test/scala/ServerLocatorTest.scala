package xsbt.boot

import java.io.File
import sbt.io.IO.withTemporaryDirectory

object ServerLocatorTest extends verify.BasicTestSuite {

  // TODO - Maybe use scalacheck to randomnly generate URIs
  test("ServerLocator read and write server URI properties") {
    withTemporaryDirectory { dir =>
      val propFile = new File(dir, "server.properties")
      val expected = new java.net.URI("http://localhost:8080")
      ServerLocator.writeProperties(propFile, expected)
      assert(ServerLocator.readProperties(propFile) == Some(expected))
    }
  }

  test("ServerLocator detect listening ports") {
    val serverSocket = new java.net.ServerSocket(0)
    object serverThread extends Thread {
      override def run(): Unit = {
        // Accept one connection.
        val result = serverSocket.accept()
        result.close()
        serverSocket.close()
      }
    }
    serverThread.start()
    val uri = new java.net.URI(
      s"http://${serverSocket.getInetAddress.getHostAddress}:${serverSocket.getLocalPort}"
    )
    assert(ServerLocator.isReachable(uri))
  }

  test("ServerLauncher detect start URI from reader") {
    val expected = new java.net.URI("http://localhost:8080")
    val input = s"""|Some random text
                    |to start the server
                    |${ServerApplication.SERVER_SYNCH_TEXT}${expected.toASCIIString}
                    |Some more output.""".stripMargin
    val inputStream = new java.io.BufferedReader(new java.io.StringReader(input))
    val result = try ServerLauncher.readUntilSynch(inputStream)
    finally inputStream.close()
    assert(result == Some(expected))
  }
}
