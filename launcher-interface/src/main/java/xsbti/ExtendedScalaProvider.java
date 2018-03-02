package xsbti;

public interface ExtendedScalaProvider extends ScalaProvider
{
	/** A ClassLoader that loads the classes from scala-library.jar. It will be the parent of `loader` .*/
	public ClassLoader loaderLibraryOnly();
}
