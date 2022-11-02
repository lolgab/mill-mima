import mill._

import mill.scalalib._
import mill.scalalib.api.Util.scalaNativeBinaryVersion
import mill.scalalib.publish._
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import mill.contrib.buildinfo.BuildInfo
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`
import de.tobiasroeser.mill.integrationtest._
import $ivy.`com.goyeau::mill-scalafix::0.2.11`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.0`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`com.github.lolgab::mill-mima::0.0.13`
import com.github.lolgab.mill.mima._
import os.Path

val millVersions = Seq("0.9.7", "0.10.0")
val millBinaryVersions = millVersions.map(scalaNativeBinaryVersion)

def millBinaryVersion(millVersion: String) = scalaNativeBinaryVersion(
  millVersion
)
def millVersion(binaryVersion: String) =
  millVersions.find(v => millBinaryVersion(v) == binaryVersion).get

trait Common extends ScalaModule with PublishModule with ScalafixModule {
  def pomSettings = PomSettings(
    description = "MiMa Mill Plugin",
    organization = "com.github.lolgab",
    url = "https://github.com/lolgab/mill-mima",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("lolgab", "mill-mima"),
    developers = Seq(
      Developer("lolgab", "Lorenzo Gabriele", "https://github.com/lolgab")
    )
  )
  def publishVersion = VcsVersion.vcsState().format()
  def scalaVersion = "2.13.8"

  def scalacOptions =
    super.scalacOptions() ++ Seq("-Ywarn-unused", "-deprecation")

  def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.6.0")
}

object `mill-mima` extends Cross[MillMimaCross](millBinaryVersions: _*)
class MillMimaCross(val millBinaryVersion: String)
    extends Common
    with BuildInfo
    with Mima {
  override def moduleDeps = super.moduleDeps ++ Seq(`mill-mima-worker-api`)
  override def artifactName = s"mill-mima_mill$millBinaryVersion"
  override def millSourcePath = super.millSourcePath / os.up
  def mimaPreviousArtifacts = Agg(
    ivy"com.github.lolgab::mima_mill$millBinaryVersion:0.0.1"
  )
  override def sources = T.sources(
    super.sources() ++ Seq(millSourcePath / s"src-mill$millBinaryVersion")
      .map(PathRef(_))
  )
  override def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion(millBinaryVersion)}"
  )
  override def buildInfoMembers = Map(
    "publishVersion" -> publishVersion()
  )
  override def buildInfoObjectName = "MimaBuildInfo"
  override def buildInfoPackageName = Some("com.github.lolgab.mill.mima.worker")
}

object `mill-mima-worker-api` extends Common
object `mill-mima-worker-impl` extends Common {
  override def moduleDeps = super.moduleDeps ++ Seq(`mill-mima-worker-api`)
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.typesafe::mima-core:1.1.1"
  )
}

object itest
    extends Cross[itestCross](
      "0.9.7",
      "0.9.12",
      "0.10.0",
      "0.10.7"
    )
class itestCross(millVersion: String) extends MillIntegrationTestModule {
  override def millSourcePath: Path = super.millSourcePath / os.up
  def millTestVersion = millVersion
  def pluginsUnderTest = Seq(`mill-mima`(millBinaryVersion(millVersion)))
  def temporaryIvyModules = Seq(
    `mill-mima-worker-impl`,
    `mill-mima-worker-api`
  )
  def testBase = millSourcePath / "src"
  override def testInvocations: T[Seq[(PathRef, Seq[TestInvocation.Targets])]] =
    T {
      Seq(
        PathRef(testBase / "simple") -> Seq(
          TestInvocation.Targets(Seq("prepare")),
          TestInvocation.Targets(Seq("verify"), expectedExitCode = 1)
        ),
        PathRef(testBase / "filters") -> Seq(
          TestInvocation.Targets(Seq("prepare")),
          TestInvocation.Targets(Seq("verify"))
        ),
        PathRef(testBase / "previous-versions") -> Seq(
          TestInvocation.Targets(Seq("prepare")),
          TestInvocation.Targets(Seq("verify")),
          TestInvocation.Targets(Seq("verifyFail"), expectedExitCode = 1),
          TestInvocation.Targets(Seq("verifyFailJs"), expectedExitCode = 1)
        ),
        PathRef(testBase / "multi-previous-versions") -> Seq(
          TestInvocation.Targets(Seq("prepare")),
          TestInvocation.Targets(Seq("verify")),
          TestInvocation.Targets(Seq("verifyFail"), expectedExitCode = 1)
        )
      )
    }
}
