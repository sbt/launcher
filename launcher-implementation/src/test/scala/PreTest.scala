/*
 * sbt
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt.boot

import java.io.File
import java.util.Arrays.equals as arrEquals
import org.scalacheck.*

object PreTest extends Properties("Pre"):
  import Pre.*
  property("isEmpty") = Prop.forAll((s: String) => (s.isEmpty == isEmpty(s)))
  property("isNonEmpty") = Prop.forAll((s: String) => (isEmpty(s) != isNonEmpty(s)))
  property("assert true") =
    assert(true); true
  property("assert false") = Prop.throws(classOf[AssertionError])(assert(false))
  property("assert true with message") = Prop.forAll { (s: String) =>
    assert(true, s); true
  }
  property("assert false with message") =
    Prop.forAll((s: String) => Prop.throws(classOf[AssertionError])(assert(false, s)))
  property("require false") =
    Prop.forAll((s: String) => Prop.throws(classOf[IllegalArgumentException])(require(false, s)))
  property("require true") = Prop.forAll { (s: String) =>
    require(true, s); true
  }
  property("error") = Prop.forAll((s: String) => Prop.throws(classOf[BootException])(error(s)))
  property("toBoolean") =
    Prop.forAll((s: String) => trap(toBoolean(s)) == trap(java.lang.Boolean.parseBoolean(s)))
  property("toArray") = Prop.forAll((list: List[Int]) => arrEquals(list.toArray, toArray(list)))
  property("toArray") =
    Prop.forAll((list: List[String]) => objArrEquals(list.toArray, toArray(list)))
  property("concat") = Prop.forAll(genFiles, genFiles) { (a: Array[File], b: Array[File]) =>
    (a ++ b) sameElements concat(a, b)
  }
  property("array") = Prop.forAll(genFiles) { (a: Array[File]) =>
    array(a.toList*) sameElements Array(a*)
  }
  property("userHomeFrom prefers a valid user.home") =
    Prop.forAll(genHome, Gen.option(genHome), genHome) { (prop, env, tmp) =>
      userHomeFrom(Some(prop), env, tmp) == prop
    }
  property("userHomeFrom falls back to HOME when user.home is invalid") =
    Prop.forAll(genInvalidHome, genHome, genHome) { (prop, env, tmp) =>
      userHomeFrom(prop, Some(env), tmp) == env
    }
  property("userHomeFrom falls back to tmpdir when both are invalid") =
    Prop.forAll(genInvalidHome, genInvalidHome, genHome) { (prop, env, tmp) =>
      userHomeFrom(prop, env, tmp) == tmp
    }
  property("fixUserHome replaces ?") =
    val original = System.getProperty("user.home")
    try
      System.setProperty("user.home", "?")
      fixUserHome()
      val fixed = System.getProperty("user.home")
      isValidHome(fixed) && fixed == userHome.toString
    finally System.setProperty("user.home", original)
  property("localCacheBaseFrom prefers sbt.global.localcache") =
    Prop.forAll(genHome, genHome, Arbitrary.arbitrary[Boolean], Arbitrary.arbitrary[Boolean]) {
      (cache, other, windows, mac) =>
        val props = Map("sbt.global.localcache" -> cache, "sbt.global.base" -> other)
        val env = Map("SBT_LOCAL_CACHE" -> other, "LOCALAPPDATA" -> other)
        localCacheBaseFrom(props.get, env.get, windows, mac, new File(other)) ==
          new File(cache).getAbsoluteFile
    }
  property("localCacheBaseFrom ignores sbt.global.base") = Prop.forAll(genHome, genHome) {
    (base, cache) =>
      val props = Map("sbt.global.base" -> base)
      val env = Map("SBT_LOCAL_CACHE" -> cache)
      localCacheBaseFrom(props.get, env.get, false, false, new File(base)) ==
        new File(cache).getAbsoluteFile
  }
  property("localCacheBaseFrom uses SBT_LOCAL_CACHE") = Prop.forAll(genHome, genHome) {
    (cache, other) =>
      val env = Map("SBT_LOCAL_CACHE" -> cache, "LOCALAPPDATA" -> other)
      localCacheBaseFrom(Map.empty.get, env.get, true, true, new File(other)) ==
        new File(cache).getAbsoluteFile
  }
  property("localCacheBaseFrom uses LOCALAPPDATA only on Windows") = Prop.forAll(genHome, genHome) {
    (app, home) =>
      val env = Map("LOCALAPPDATA" -> app)
      localCacheBaseFrom(Map.empty.get, env.get, true, false, new File(home)) ==
        new File(app, "sbt").getAbsoluteFile &&
        localCacheBaseFrom(Map.empty.get, env.get, false, false, new File(home)) ==
        new File(home, ".cache/sbt").getAbsoluteFile
  }
  property("localCacheBaseFrom prefers XDG_CACHE_HOME over LOCALAPPDATA") =
    Prop.forAll(genHome, genHome, genHome) { (xdg, app, home) =>
      val env = Map("XDG_CACHE_HOME" -> xdg, "LOCALAPPDATA" -> app)
      localCacheBaseFrom(Map.empty.get, env.get, true, false, new File(home)) ==
        new File(xdg, "sbt").getAbsoluteFile
    }
  property("localCacheBaseFrom uses Library/Caches on macOS") = Prop.forAll(genHome) { home =>
    localCacheBaseFrom(Map.empty.get, Map.empty.get, false, true, new File(home)) ==
      new File(home, "Library/Caches/sbt").getAbsoluteFile
  }
  property("localCacheBaseFrom prefers XDG_CACHE_HOME over macOS default") =
    Prop.forAll(genHome, genHome) { (xdg, home) =>
      val env = Map("XDG_CACHE_HOME" -> xdg)
      localCacheBaseFrom(Map.empty.get, env.get, false, true, new File(home)) ==
        new File(xdg, "sbt").getAbsoluteFile
    }
  property("localCacheBaseFrom uses XDG_CACHE_HOME") = Prop.forAll(genHome, genHome) {
    (xdg, home) =>
      val env = Map("XDG_CACHE_HOME" -> xdg)
      localCacheBaseFrom(Map.empty.get, env.get, false, false, new File(home)) ==
        new File(xdg, "sbt").getAbsoluteFile
  }
  property("substituteTilde") =
    val userHome = Pre.userHome.toString
    assert(substituteTilde("~/path") == s"$userHome/path")
    assert(substituteTilde("~\\") == s"$userHome\\")
    assert(substituteTilde("~") == userHome)
    assert(substituteTilde("~x") == "~x")
    assert(substituteTilde("x~/") == "x~/")
    true

  given Arbitrary[File] = Arbitrary {
    for (i <- Arbitrary.arbitrary[Int]) yield new File(i.toString)
  }
  val genFiles: Gen[Array[File]] = Arbitrary.arbitrary[Array[File]]
  val genHome: Gen[String] = Gen.identifier.map("/" + _)
  val genInvalidHome: Gen[Option[String]] =
    Gen.oneOf(None, Some(""), Some("?"), Some("  "), Some(" ? "))

  def trap[T](t: => T): Option[T] =
    try Some(t)
    catch case _: Exception => None

  private def objArrEquals[T <: AnyRef](a: Array[T], b: Array[T]): Boolean =
    arrEquals(a.asInstanceOf[Array[AnyRef]], b.asInstanceOf[Array[AnyRef]])
