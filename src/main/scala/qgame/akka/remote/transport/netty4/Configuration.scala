package qgame.akka.remote.transport.netty4

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

/**
 * Created by kerr.
 */
case class Configuration(underlying: Config) {
  def readValue[T](key: String, v: => T): Option[T] = {
    if (underlying.hasPath(key)) {
      Option(v)
    } else {
      None
    }
  }

  def getString(key: String): Option[String] = readValue(key, underlying.getString(key))

  def getString(key: String, default: String): String = getString(key).getOrElse(default)

  def getInt(key: String): Option[Int] = readValue(key, underlying.getInt(key))

  def getInt(key: String, default: Int): Int = getInt(key).getOrElse(default)

  def getBoolean(key: String): Option[Boolean] = readValue(key, underlying.getBoolean(key))

  def getBoolean(key: String, default: Boolean): Boolean = getBoolean(key).getOrElse(default)

  def getMilliseconds(key: String): Option[Long] = readValue(key, underlying.getDuration(key, TimeUnit.MILLISECONDS))

  def getMilliseconds(key: String, default: Long): Long = getMilliseconds(key).getOrElse(default)

  def getNanoseconds(key: String): Option[Long] = readValue(key, underlying.getDuration(key, TimeUnit.NANOSECONDS))

  def getNanoseconds(key: String, default: Long): Long = getNanoseconds(key).getOrElse(default)

  def getDuration(key: String, timeUnit: TimeUnit): Option[Long] = readValue(key, underlying.getDuration(key, timeUnit))

  def getDuration(key: String, default: Long, timeUnit: TimeUnit): Long = getDuration(key, timeUnit).getOrElse(default)

  def getBytes(key: String): Option[Long] = readValue(key, underlying.getBytes(key))

  def getBytes(key: String, default: Long): Long = getBytes(key).getOrElse(default)
}
