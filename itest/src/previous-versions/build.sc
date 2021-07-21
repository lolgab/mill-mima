import $exec.plugins

import com.github.lolgab.mill.mima._
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalajslib.ScalaJSModule

trait Common extends ScalaModule with PublishModule with Mima { outer =>
  def scalaVersion = "2.13.6"
  def publishVersion = "0.0.1"
  def pomSettings =
    PomSettings("", organization = "org", "", Seq(), VersionControl(), Seq())
  trait Js extends Common with ScalaJSModule {
    override def scalaJSVersion = "1.6.0"
    override def mimaPreviousVersions = outer.mimaPreviousVersions
  }
}
object prev extends Common {
  object js extends Js
}
object curr extends Common with Mima { outer =>
  override def mimaPreviousVersions = T(Seq("0.0.1"))
  object js extends Js
}

def prepare() = T.command {
  prev.publishLocal(sys.props("ivy.home") + "/local")()
  prev.js.publishLocal(sys.props("ivy.home") + "/local")()
}

def verify() = T.command {
  // tests mimaPreviousVersions
  assert(curr.mimaPreviousArtifacts() == Agg(ivy"org::prev:0.0.1"))
  assert(curr.js.mimaPreviousArtifacts() == Agg(ivy"org::prev::0.0.1"))
  // tests resolution and issue reporting
  curr.mimaReportBinaryIssues()()
  curr.js.mimaReportBinaryIssues()()
}
