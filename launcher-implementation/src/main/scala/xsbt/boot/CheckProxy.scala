/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.boot

import Pre._
import java.net.{ MalformedURLException, URL }

object CheckProxy {
  def apply(): Unit = {
    import ProxyProperties._
    for (pp <- Seq(http, https, ftp))
      setFromEnv(pp)
  }

  private[this] def setFromEnv(conf: ProxyProperties): Unit = {
    import conf._
    val proxyURL = System.getenv(envURL)
    if (isDefined(proxyURL) && !isPropertyDefined(sysHost) && !isPropertyDefined(sysPort)) {
      try {
        val proxy = new URL(proxyURL)
        setProperty(sysHost, proxy.getHost)
        val port = proxy.getPort
        if (port >= 0)
          System.setProperty(sysPort, port.toString)
        copyEnv(envUser, sysUser)
        copyEnv(envPassword, sysPassword)
      } catch {
        case e: MalformedURLException =>
          Console.err.println(s"[warn] could not parse $envURL setting: ${e.toString}")
      }
    }
  }

  private def copyEnv(envKey: String, sysKey: String): Unit = { setProperty(sysKey, System.getenv(envKey)) }
  private def setProperty(key: String, value: String): Unit = { if (value != null) System.setProperty(key, value) }
  private def isPropertyDefined(k: String) = isDefined(System.getProperty(k))
  private def isDefined(s: String) = s != null && isNonEmpty(s)
}
