/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

import BootConfiguration.{ FjbgPackage, IvyPackage, SbtBootPackage, ScalaPackage }
import scala.annotation.tailrec
import scala.collection.immutable.LazyList

/**
 * A custom class loader to ensure the main part of sbt doesn't load any Scala or
 * Ivy classes from the jar containing the loader.
 */
private[boot] final class BootFilteredLoader(parent: ClassLoader) extends ClassLoader(parent):
  @throws(classOf[ClassNotFoundException])
  override final def loadClass(className: String, resolve: Boolean): Class[?] =
    // note that we allow xsbti.*
    if className.startsWith(ScalaPackage) || className.startsWith(IvyPackage) || className
        .startsWith(SbtBootPackage) || className.startsWith(FjbgPackage)
    then throw new ClassNotFoundException(className)
    else super.loadClass(className, resolve)
  override def getResources(name: String) = excludedLoader.getResources(name)
  override def getResource(name: String) = excludedLoader.getResource(name)

  // the loader to use when a resource is excluded.  This needs to be at least parent.getParent so that it skips parent.  parent contains
  // resources included in the launcher, which need to be ignored.  Now that the launcher can be unrooted (not the application entry point),
  // this needs to be the Java extension loader (the loader with getParent == null)
  private val excludedLoader = Loaders(parent.getParent).head

object Loaders:
  def apply(loader: ClassLoader): LazyList[ClassLoader] =
    @tailrec
    def loaders(loader: ClassLoader, accum: LazyList[ClassLoader]): LazyList[ClassLoader] =
      if loader eq null then accum else loaders(loader.getParent, loader #:: accum)
    loaders(loader, LazyList.empty)
