/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

import java.io.{ Closeable, File, FileInputStream, FileOutputStream, InputStream, OutputStream }
import scala.annotation.tailrec

object Using:
  def apply[R <: Closeable, T](create: R)(f: R => T): T = withResource(create)(f)
  def withResource[R <: Closeable, T](r: R)(f: R => T): T =
    try
      f(r)
    finally
      r.close()

object Copy:
  def apply(files: List[File], toDirectory: File): Boolean =
    files.map(file => apply(file, toDirectory)).contains(true)
  def apply(file: File, toDirectory: File): Boolean =
    toDirectory.mkdirs()
    val to = new File(toDirectory, file.getName)
    val missing = !to.exists
    if missing then
      Using(new FileInputStream(file)) { in =>
        Using(new FileOutputStream(to)) { out =>
          transfer(in, out)
        }
      }
    missing
  def transfer(in: InputStream, out: OutputStream): Unit =
    val buffer = new Array[Byte](8192)
    @tailrec
    def next(): Unit =
      val read = in.read(buffer)
      if read > 0 then
        out.write(buffer, 0, read)
        next()
    next()
