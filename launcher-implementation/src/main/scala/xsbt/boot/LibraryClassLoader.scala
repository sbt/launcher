package xsbt.boot

import java.net.{ URL, URLClassLoader }

final class LibraryClassLoader(urls: Array[URL], parent: ClassLoader,
  val scalaVersion: String) extends URLClassLoader(urls, parent) with xsbti.LibraryClassLoader {
  override def toString: String = s"LibraryClassLoader(jar = ${urls.head}, parent = $parent)"
}
