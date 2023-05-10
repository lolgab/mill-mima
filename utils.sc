import coursier._
import scala.concurrent.duration._

def findLatestDevVersion(): String = {
  val versions =
    Versions(cache.FileCache().withTtl(1.minute))
      .withModule(mod"com.lihaoyi:mill-main_2.13")
      .run()
  versions.latest
}
