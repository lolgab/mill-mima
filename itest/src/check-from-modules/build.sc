import mill._

import mill.scalalib._
import mill.scalalib.publish._
import $exec.plugins
import com.github.lolgab.mill.mima._

trait Common extends ScalaModule with PublishModule {
  def scalaVersion = "2.13.4"
  def publishVersion = "0.0.1"
  def pomSettings =
    PomSettings("", organization = "org", "", Seq(), VersionControl(), Seq())
}
object prev extends Common
object curr extends Common with Mima {
  override def mimaModules = Seq(prev)
}

def verify() = T.command {
  curr.mimaReportBinaryIssues()()
  ()
}
