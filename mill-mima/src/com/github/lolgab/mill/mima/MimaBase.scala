package com.github.lolgab.mill.mima

import com.github.lolgab.mill.mima.internal.Utils.scalaBinaryVersion
import com.github.lolgab.mill.mima.worker.MimaBuildInfo
import com.github.lolgab.mill.mima.worker.MimaWorkerExternalModule
import mill._
import mill.api.Result
import mill.define.Command
import mill.define.Target
import mill.define.Task
import mill.scalalib._

import scala.jdk.CollectionConverters._
import scala.util.chaining._

private[mima] trait MimaBase
    extends ScalaModule
    with PublishModule
    with ExtraCoursierSupport {

  /** Set of versions to check binary compatibility against. */
  def mimaPreviousVersions: Target[Seq[String]] = T { Seq.empty[String] }

  /** Set of artifacts to check binary compatibility against. By default this is
    * derived from [[mimaPreviousVersions]].
    */
  def mimaPreviousArtifacts: Target[Agg[Dep]] = T {
    val versions = mimaPreviousVersions().distinct
    if (versions.isEmpty)
      Result.Failure(
        "No previous artifacts configured. Please override mimaPreviousVersions or mimaPreviousArtifacts.",
        Some(Agg.empty[Dep])
      )
    else
      Result.Success(
        Agg.from(
          versions.map(version =>
            ivy"${pomSettings().organization}:${artifactId()}:${version}"
          )
        )
      )
  }

  def mimaCheckDirection: Target[CheckDirection] = T { CheckDirection.Backward }

  private[mima] def resolvedMimaPreviousArtifacts: T[Agg[(Dep, PathRef)]] = T {
    resolveSeparateNonTransitiveDeps(mimaPreviousArtifacts)().map(p =>
      p._1 -> p._2.iterator.next()
    )
  }

  /** Filters to apply to binary issues found. Applies both to backward and
    * forward binary compatibility checking.
    */
  def mimaBinaryIssueFilters: Target[Seq[ProblemFilter]] = T {
    Seq.empty[ProblemFilter]
  }

  /** Filters to apply to binary issues found grouped by version of a module
    * checked against. These filters only apply to backward compatibility
    * checking.
    */
  def mimaBackwardIssueFilters: Target[Map[String, Seq[ProblemFilter]]] = T {
    Map.empty[String, Seq[ProblemFilter]]
  }

  /** Filters to apply to binary issues found grouped by version of a module
    * checked against. These filters only apply to forward compatibility
    * checking.
    */
  def mimaForwardIssueFilters: Target[Map[String, Seq[ProblemFilter]]] = T {
    Map.empty[String, Seq[ProblemFilter]]
  }

  /** The fully-qualified class names of annotations that exclude parts of the
    * API from problem checking.
    */
  def mimaExcludeAnnotations: Target[Seq[String]] = T {
    Seq.empty[String]
  }

  private def mimaWorkerClasspath: T[Agg[os.Path]] = T {
    Lib
      .resolveDependencies(
        repositoriesTask(),
        resolveCoursierDependency().apply(_),
        Agg(
          ivy"com.github.lolgab:mill-mima-worker-impl_2.13:${MimaBuildInfo.publishVersion}"
            .exclude("com.github.lolgab" -> "mill-mima-worker-api_2.13")
        ),
        ctx = Some(T.log)
      )
      .map(_.map(_.path))
  }

  private def mimaWorker: Task[worker.api.MimaWorkerApi] = T.task {
    val cp = mimaWorkerClasspath()
    MimaWorkerExternalModule.mimaWorker().impl(cp)
  }

  def mimaReportBinaryIssues(): Command[Unit] = T.command {
    def prettyDep(dep: Dep): String = {
      s"${dep.dep.module.orgName}:${dep.dep.version}"
    }
    val log = T.ctx().log
    val logDebug: java.util.function.Consumer[String] = log.debug(_)
    val logError: java.util.function.Consumer[String] = log.error(_)
    val logPrintln: java.util.function.Consumer[String] =
      log.outputStream.println(_)
    val runClasspathIO =
      runClasspath().view.map(_.path).filter(os.exists).map(_.toIO).toArray
    val current = compile().classes.path.pipe {
      case p if os.exists(p) => p
      case _                 => (T.dest / "emptyClasses").tap(os.makeDir)
    }.toIO

    val previous = resolvedMimaPreviousArtifacts().iterator.map {
      case (dep, artifact) =>
        new worker.api.Artifact(prettyDep(dep), artifact.path.toIO)
    }.toArray

    val checkDirection = mimaCheckDirection() match {
      case CheckDirection.Forward  => worker.api.CheckDirection.Forward
      case CheckDirection.Backward => worker.api.CheckDirection.Backward
      case CheckDirection.Both     => worker.api.CheckDirection.Both
    }

    def toWorkerApi(p: ProblemFilter) =
      new worker.api.ProblemFilter(p.name, p.problem)

    val binaryFilters = mimaBinaryIssueFilters().map(toWorkerApi).toArray
    val backwardFilters =
      mimaBackwardIssueFilters().view
        .mapValues(_.map(toWorkerApi).toArray)
        .toMap
        .asJava
    val forwardFilters =
      mimaForwardIssueFilters().view
        .mapValues(_.map(toWorkerApi).toArray)
        .toMap
        .asJava

    val errorOpt: java.util.Optional[String] = mimaWorker().reportBinaryIssues(
      scalaBinaryVersion(scalaVersion()),
      logDebug,
      logError,
      logPrintln,
      checkDirection,
      runClasspathIO,
      previous,
      current,
      binaryFilters,
      backwardFilters,
      forwardFilters,
      mimaExcludeAnnotations().toArray,
      publishVersion()
    )

    if (errorOpt.isPresent()) Result.Failure(errorOpt.get())
    else Result.Success(())
  }

}
