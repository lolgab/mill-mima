import mill._
import mill.scalalib._
import mill.scalalib.publish._
import $exec.plugins
import com.github.lolgab.mill.mima._
import coursier.ivy.IvyRepository
import mill.define.Target
import os.Path

trait Common extends ScalaModule with PublishModule {
  def scalaVersion = "2.13.4"
  def publishVersion = "0.0.1"
  def pomSettings =
    PomSettings("", organization = "org", "", Seq(), VersionControl(), Seq())
}
object prev extends Common
object prev2 extends Common {
  override def millSourcePath = prev.millSourcePath
  override def artifactName = "prev"
  override def publishVersion = "0.0.2"
}
object prev3 extends Common {
  override def millSourcePath = curr.millSourcePath
  override def artifactName = "prev"
  override def publishVersion = "0.0.3"
}
object curr extends Common with Mima {
  def mimaPreviousArtifacts = T(
    Agg(
      ivy"org::prev:0.0.1",
      ivy"org::prev:0.0.2",
      ivy"org::prev:0.0.3"
    )
  )
}

val repo = sys.props("ivy.home") + "/local"

def prepare() = T.command {
  prev.publishLocal(repo)()
  prev2.publishLocal(repo)()
  prev3.publishLocal(repo)()
  ()
}

def verify() = T.command {

  assert(curr.mimaPreviousArtifacts().iterator.size == 3)

  // need to resolve all deps, even when they have same org and name
  // FIXME: can't actually assert that, as there is no access to this in-between result
  // assert(curr.resolvedMimaPreviousArtifacts().size == 3)

  // expect 4 issue (2 from prev, 2 from prev2, 0 from prev3)
  // If the target returns the issues someday, we should assert a concrete count here
  curr.mimaReportBinaryIssues()()

  ()
}
