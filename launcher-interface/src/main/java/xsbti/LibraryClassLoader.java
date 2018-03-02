package xsbti;

import java.io.File;

/**
 * Marker interface for classloader with just scala-library.
 */
public interface LibraryClassLoader
{
  public String scalaVersion();
}
