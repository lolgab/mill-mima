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
  override def millSourcePath = curr.millSourcePath
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
      ivy"org::prev:0.0.2"
    )
  )
}

object curr2 extends Common with Mima {
  override def millSourcePath = curr.millSourcePath
  def mimaPreviousArtifacts = T(
    Agg(
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
  assert(curr.mimaPreviousArtifacts().iterator.size == 2)
  assert(curr2.mimaPreviousArtifacts().iterator.size == 2)
  curr2.mimaReportBinaryIssues()()
  ()
}

def verifyFail() = T.command {
  curr.mimaReportBinaryIssues()()
}
