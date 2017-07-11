/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.boot

import Pre._
import java.net.{ MalformedURLException, URL }

object CheckProxy {
  def apply() {
    import ProxyProperties._
    for (pp <- Seq(http, https, ftp))
      setFromEnv(pp)

    // https will use http.nonProxyHosts
    val propsToSet = Seq(http.sysNonProxyHosts, ftp.sysNonProxyHosts)
      .filterNot(isPropertyDefined)
      .map(prop => (noProxy: String) => setProperty(prop, noProxy))

    if (propsToSet.nonEmpty)
      nonProxyHosts.foreach(noProxy => propsToSet.foreach(f => f(noProxy)))
  }

  private[this] def setFromEnv(conf: ProxyProperties) {
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
          System.err.println(s"Warning: could not parse $envURL setting: ${e.toString}")
      }
    }
  }

  private[this] def nonProxyHosts: Option[String] = {
    import ProxyProperties.envNoProxy
    val noProxy = System.getenv(envNoProxy)
    if (isDefined(noProxy)) {
      if (noProxy == '*') Some("*")
      else {
        Some(noProxy.split("""[,\s]""").foldLeft(List.empty[String]) {
          case (acc, proxy) =>
            if (proxy.isEmpty) acc
            else {
              proxy.split('/') match {
                case Array(ip, prefixLen) if ip.contains(":") => acc ++ ipv6CidrToRange(ip, prefixLen.toInt)
                case Array(ip, prefixLen)                     => acc ++ ipv4CidrToRange(ip, prefixLen.toInt)
                case Array(host)                              => acc :+ (if (host.startsWith(".")) s"*$host" else host)
              }
            }
        }.mkString("|"))
      }
    } else None
  }

  private def ipv4CidrToRange(ip: String, prefixLen: Int): List[String] = {
    val startRange = ip.split('.').map(_.toInt)

    val end = (24 to 0 by -8).zipWithIndex.foldLeft(0) {
      case (acc, i) =>
        (startRange(i._2) << i._1) + acc
    } | ~(-1 << (32 - prefixLen))

    val endRange = (24 to 0 by -8).map(x => end >> x & 0xff)

    val block = (2 to 0 by -1).foldLeft(generateRange(startRange(3), endRange(3))) {
      case (acc, i) => generatePrefix(generateRange(startRange(i), endRange(i)), acc)('.')
    }

    block match {
      case Right(s) => List(s)
      case Left(xs) => xs
    }
  }

  private def ipv6CidrToRange(ip: String, prefixLen: Int): List[String] = {
    import scala.BigInt._

    // check for compressed ipv6 address
    val start =
      (if (ip.contains("::")) {
        val blk = ip.split("::", -1).map(x => if (x.isEmpty) "0" else x) // always have 2 elements
        val (prefix, suffix) = (blk.head.split(':'), blk.last.split(':'))
        Array(prefix, Array.fill(8 - (prefix.length + suffix.length))("0"), suffix).flatten.mkString(":")
      } else ip).replaceAll("""[\[\]]""", "")

    val startRange = start.split(':').map(Integer.parseInt(_, 16))

    val end: BigInt = (112 to 0 by -16).zipWithIndex.foldLeft(BigInt(0)) {
      case (acc, i) =>
        (BigInt(startRange(i._2)) << i._1) + acc
    } | ~(BigInt(-1) << (128 - prefixLen))

    val endRange = (112 to 0 by -16).map(x => end >> x & 0xffff)

    val block = (6 to 0 by -1).foldLeft(generateRange6(startRange(7), endRange(7))) {
      case (acc, i) => generatePrefix(generateRange6(startRange(i), endRange(i)), acc)(':')
    }

    def toHex(s: String) = if (s == "*") "*" else f"${s.toInt}%04x"

    block match {
      case Right(s) => List(s"[${s.split(':').map(toHex).mkString(":")}]")
      case Left(xs) => xs.map(s => s"[${s.split(':').map(toHex).mkString(":")}]")
    }
  }

  private def generateRange6(start: BigInt, end: BigInt): Either[List[String], String] =
    if (start == 0 && end == 65535) Right("*")
    else if (start == end) Right(start.toString)
    else Left((start to end).foldLeft(List.empty[String]) { case (acc, i) => acc :+ i.toString })

  def generatePrefix(start: Either[List[String], String], end: Either[List[String], String])(sep: Char): Either[List[String], String] =
    (start, end) match {
      case (Right(s1), Right(s2)) if s1 == "*" && s2 == "*" => Right(s"$s1")
      case (Right(s1), Right(s2))                           => Right(s"$s1$sep$s2")
      case (Right(s1), Left(xs))                            => Left(xs.map(s => s"$s1$sep$s"))
      case (Left(xs), Right(s2))                            => Left(xs.map(s => s"$s$sep$s2"))
      case (Left(xs1), Left(xs2))                           => Left(xs1.zip(xs2).map { case (s1, s2) => s"$s1$sep$s2" })
    }

  private def generateRange(start: Int, end: Int): Either[List[String], String] =
    if (start == 0 && end == 255) Right("*")
    else if (start == end) Right(start.toString)
    else Left((start to end).foldLeft(List.empty[String]) { case (acc, i) => acc :+ i.toString })

  private def copyEnv(envKey: String, sysKey: String) { setProperty(sysKey, System.getenv(envKey)) }
  private def setProperty(key: String, value: String) { if (value != null) System.setProperty(key, value) }
  private def isPropertyDefined(k: String) = isDefined(System.getProperty(k))
  private def isDefined(s: String) = s != null && isNonEmpty(s)
}
