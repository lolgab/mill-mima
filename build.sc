import mill._, mill.scalalib._
import mill.scalalib.publish._
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest_mill0.9:0.4.1`
import de.tobiasroeser.mill.integrationtest._
import $ivy.`com.goyeau::mill-scalafix:0.2.1`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.1`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`com.github.lolgab::mima_mill0.9:0.0.1`
import com.github.lolgab.mill.mima._

object `mill-mima` extends ScalaModule with PublishModule with ScalafixModule with Mima {
  def mimaPreviousArtifacts = Agg(ivy"com.github.lolgab::mima_mill0.9:0.0.1")
  override def artifactName = s"${super.artifactName()}_mill$millBinaryVersion"
  def pomSettings = PomSettings(
    description = "MiMa Mill Plugin",
    organization = "com.github.lolgab",
    url = "https://github.com/lolgab/mill-mima",
    licenses = Seq(License.`Apache-2.0`),
    scm = SCM(
      "git://github.com/lolgab/mill-mima.git",
      "scm:git://github.com/lolgab/mill-mima.git"
    ),
    developers = Seq(
      Developer("lolgab", "Lorenzo Gabriele", "https://github.com/lolgab")
    )
  )
  def publishVersion = VcsVersion.vcsState().format()
  def scalaVersion = "2.13.4"
  def millVersion = "0.9.8"
  def millBinaryVersion = millVersion.split('.').take(2).mkString(".")
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalalib:$millVersion",
    ivy"com.typesafe::mima-core:0.9.2"
  )

  def scalacOptions = super.scalacOptions() ++ Seq("-Ywarn-unused", "-deprecation")

  def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.4.4")
}

object itest extends MillIntegrationTestModule {
  def millTestVersion = "0.9.8"
  def pluginsUnderTest = Seq(`mill-mima`)
}
