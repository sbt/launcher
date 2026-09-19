case class JdkCross(
    override val idSuffix: String,
    override val directorySuffix: String
) extends sbt.VirtualAxis.WeakAxis
