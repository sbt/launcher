/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot.test

import java.net.Socket
import java.net.SocketTimeoutException
import scala.annotation.tailrec

class EchoServer extends xsbti.ServerMain:
  def start(configuration: xsbti.AppConfiguration): xsbti.Server =
    object server extends xsbti.Server:
      // TODO - Start a server.
      val serverSocket = new java.net.ServerSocket(0)
      val port = serverSocket.getLocalPort
      val addr = serverSocket.getInetAddress.getHostAddress
      override val uri = new java.net.URI(s"http://${addr}:${port}")
      // Check for stop every second.
      serverSocket.setSoTimeout(1000)
      object serverThread extends Thread:
        private val running = new java.util.concurrent.atomic.AtomicBoolean(true)
        override def run(): Unit =
          while running.get do
            try
              val clientSocket = serverSocket.accept()
              // Handle client connections
              object clientSocketThread extends Thread:
                override def run(): Unit =
                  echoTo(clientSocket)
              clientSocketThread.start()
            catch
              case _: SocketTimeoutException => // Ignore
        // Simple mechanism to dump input to output.
        private def echoTo(socket: Socket): Unit =
          val input =
            new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream))
          val output =
            new java.io.BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream))
          import scala.util.control.Breaks.*
          try
            // Lame way to break out.
            breakable {
              @tailrec
              def read(): Unit = input.readLine match
                case null   => ()
                case "kill" =>
                  running.set(false)
                  serverSocket.close()
                  break()
                case line =>
                  output.write(line)
                  output.flush()
                  read()
              read()
            }
          finally
            output.close()
            input.close()
            socket.close()
      // Start the thread immediately
      serverThread.start()
      override def awaitTermination(): xsbti.MainResult =
        serverThread.join()
        new Exit(0)
    server
