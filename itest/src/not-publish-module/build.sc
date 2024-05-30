import mill._

import mill.scalalib._
import mill.scalalib.publish._
import $file.plugins
import com.github.lolgab.mill.mima._

object prev extends ScalaModule with PublishModule {
  def scalaVersion = "2.13.4"
  def publishVersion = "0.0.1"
  def pomSettings =
    PomSettings("", organization = "org", "", Seq(), VersionControl(), Seq())
}
object curr extends ScalaModule with Mima {
  def scalaVersion = "2.13.4"
  override def mimaPreviousArtifacts = T(Agg(ivy"org::prev:0.0.1"))
}

def prepare() = T.command {
  prev.publishLocal(sys.props("ivy.home") + "/local")()
}

def verify() = T.command {
  curr.mimaReportBinaryIssues()()
  ()
}
