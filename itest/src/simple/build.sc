import mill._, mill.scalalib._, mill.scalalib.publish._
import $exec.plugins
import com.github.lolgab.mill.mima._
import coursier.ivy.IvyRepository

trait Common extends ScalaModule with PublishModule {
  def scalaVersion = "2.13.4"
  def publishVersion = "0.0.1"
  def pomSettings =
    PomSettings("", organization = "org", "", Seq(), VersionControl(), Seq())
}
object prev extends Common
object curr extends Common with Mima {
  def mimaPreviousArtifacts = T(Agg(ivy"org::prev:0.0.1"))
}

def verify() = T.command {
  val publishPath = prev.repositories.collectFirst { case r: IvyRepository =>
    r.pattern.string.stripPrefix("file:").takeWhile(_ != '[').dropRight(1)
  }
  prev.publishLocal(localIvyRepo = publishPath.get)
  curr.mimaReportBinaryIssues()
}
