import sbt.*
import java.util.jar.{ Attributes, JarFile, JarOutputStream, Manifest }
import java.util.zip.{ ZipEntry, ZipFile }
import scala.collection.JavaConverters.*

/**
 * Creates a multi-release JAR (JEP 238) out of a base JAR and JARs for newer Java versions.
 */
object MultiReleaseJar {
  private val manifestName = "META-INF/MANIFEST.MF"

  private class Content(val entry: ZipEntry, val bytes: Array[Byte])

  private def read(jar: File): Vector[Content] = {
    val zip = new ZipFile(jar)
    try {
      zip.entries.asScala.map { e =>
        val bytes =
          if (e.isDirectory) Array.emptyByteArray
          else {
            val in = zip.getInputStream(e)
            try IO.readBytes(in)
            finally in.close()
          }
        new Content(e, bytes)
      }.toVector
    } finally zip.close()
  }

  private def manifestOf(jar: File): Manifest = {
    val jf = new JarFile(jar)
    try Option(jf.getManifest).map(new Manifest(_)).getOrElse(new Manifest())
    finally jf.close()
  }

  /**
   * Writes `output` with the contents of `base` at the root, and the contents of each of
   * `versioned` under META-INF/versions/<n>/. Versioned entries that are byte-for-byte identical
   * to the base entry are omitted, since the base entry is used as a fallback.
   */
  def create(base: File, versioned: Seq[(Int, File)], output: File): File = {
    val baseContents = read(base)
    val baseBytes = baseContents.iterator
      .filterNot(_.entry.isDirectory)
      .map(c => c.entry.getName -> c.bytes)
      .toMap

    val manifest = manifestOf(base)
    if (manifest.getMainAttributes.getValue(Attributes.Name.MANIFEST_VERSION) == null)
      manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    manifest.getMainAttributes.putValue("Multi-Release", "true")

    IO.createDirectory(output.getParentFile)
    val out = new JarOutputStream(new java.io.FileOutputStream(output))
    try {
      val written = scala.collection.mutable.Set[String]()
      def put(name: String, time: Long, bytes: Array[Byte]): Unit =
        if (written.add(name)) {
          val e = new ZipEntry(name)
          e.setTime(time)
          out.putNextEntry(e)
          out.write(bytes)
          out.closeEntry()
        }

      // the manifest has to be the first entry
      val mf = new java.io.ByteArrayOutputStream()
      manifest.write(mf)
      put(manifestName, 0L, mf.toByteArray)

      baseContents.filterNot(_.entry.getName == manifestName).foreach { c =>
        put(c.entry.getName, c.entry.getTime, c.bytes)
      }
      versioned.sortBy(_._1).foreach { case (version, jar) =>
        read(jar).foreach { c =>
          val name = c.entry.getName
          // only META-INF/services is honored under META-INF/versions/<n>/
          val skip = c.entry.isDirectory ||
            (name.startsWith("META-INF/") && !name.startsWith("META-INF/services/"))
          val unchanged = baseBytes.get(name).exists(java.util.Arrays.equals(_, c.bytes))
          if (!skip && !unchanged)
            put(s"META-INF/versions/$version/$name", c.entry.getTime, c.bytes)
        }
      }
    } finally out.close()
    output
  }
}
