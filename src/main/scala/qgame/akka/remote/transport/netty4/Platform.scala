package qgame.akka.remote.transport.netty4

import java.io.File

/**
 * Created by kerr.
 */
object Platform {
  private val osName = sys.props("os.name")

  def isUnix: Boolean =File.separatorChar == '/'

  def isWindows :Boolean = File.separatorChar == '\\'

  def isLinux:Boolean = isUnix && osName.toLowerCase.contains("linux")
}
