import mill._, mill.scalalib._, mill.scalalib.publish._
import $exec.plugins
import com.github.lolgab.mill.mima._
import com.typesafe.tools.mima.core.Problem
import coursier.ivy.IvyRepository

trait Common extends ScalaModule with PublishModule {
  def scalaVersion = "2.13.4"
  def publishVersion = "0.0.1"
  def pomSettings =
    PomSettings("", organization = "org", "", Seq(), VersionControl(), Seq())
}
object prev extends Common
object curr extends Common with Mima {
  override def mimaPreviousArtifacts = T(Agg(ivy"org::prev:0.0.1"))
  override def mimaBinaryIssueFilters = T {
    Seq(ProblemFilter.exclude[Problem]("foo.Foo.*"))
  }
}

def prepare() = T.command {
  prev.publishLocal(sys.props("ivy.home") + "/local")()
}

def verify() = T.command {
  curr.mimaReportBinaryIssues()
}
