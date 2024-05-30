package com.github.lolgab.mill.mima

import com.github.lolgab.mill.mima.internal.Utils.scalaBinaryVersion
import com.github.lolgab.mill.mima.worker.MimaWorkerExternalModule
import mill._
import mill.api.Result
import mill.define.Command
import mill.define.Target
import mill.define.Task
import mill.scalalib._

import scala.util.chaining._

private[mima] trait MimaBase
    extends JavaModule
    with ExtraCoursierSupport
    with VersionSpecific {

  private def publishDataTask: Task[Option[(String, String, String)]] =
    this match {
      case m: PublishModule =>
        T.task {
          Some(
            (m.pomSettings().organization, m.artifactId(), m.publishVersion())
          )
        }
      case _ => T.task { None }
    }

  /** Set of versions to check binary compatibility against. */
  def mimaPreviousVersions: Target[Seq[String]] = T { Seq.empty[String] }

  /** Set of artifacts to check binary compatibility against. By default this is
    * derived from [[mimaPreviousVersions]].
    */
  def mimaPreviousArtifacts: Target[Agg[Dep]] = T {
    val publishData = publishDataTask()
    val versions = mimaPreviousVersions().distinct
    if (versions.isEmpty)
      Result.Failure(
        "No previous artifacts configured. Please override mimaPreviousVersions or mimaPreviousArtifacts.",
        Some(Agg.empty[Dep])
      )
    else
      publishData match {
        case Some((organization, artifactId, _)) =>
          Result.Success(
            Agg.from(
              versions.map(version => ivy"$organization:$artifactId:$version")
            )
          )
        case None =>
          Result.Failure(
            "The module is not a PublishModule so it requires setting mimaPreviousArtifacts manually. Please override mimaPreviousArtifacts or extend PublishModule.",
            Some(Agg.empty[Dep])
          )
      }
  }

  private def mimaCheckDirectionInput = T.input {
    T.env.get("MIMA_CHECK_DIRECTION")
  }

  /** Compatibility checking direction. */
  def mimaCheckDirection: Target[CheckDirection] = T {
    mimaCheckDirectionInput() match {
      case Some("both")            => Result.Success(CheckDirection.Both)
      case Some("forward")         => Result.Success(CheckDirection.Forward)
      case Some("backward") | None => Result.Success(CheckDirection.Backward)
      case Some(other) =>
        Result.Failure(
          s"Invalid check direction \"$other\". Valid values are \"backward\", \"forward\" or \"both\"."
        )
    }
  }

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

  /** If true, report `IncompatibleSignatureProblem`s.
    */
  def mimaReportSignatureProblems: Target[Boolean] = T {
    false
  }

  private def mimaWorker: Task[worker.api.MimaWorkerApi] = T.task {
    val cp = mimaWorkerClasspath()
    MimaWorkerExternalModule.mimaWorker().impl(cp)
  }

  /** The `PathRef` to the actual artifact that is being checked for binary
    * compatibility. Defaults to use the result of the [[jar]] target.
    *
    * Up until version mill-mima `0.0.24`, this was implemented as
    * [[compile]]`().classes`, for compatibility to the sbt plugin.
    */
  def mimaCurrentArtifact: T[PathRef] = T { jar() }

  def mimaReportBinaryIssues(): Command[Unit] = {
    val scalaBinVersionTask: Task[Option[String]] = this match {
      case m: ScalaModule =>
        T.task { Some(scalaBinaryVersion(m.scalaVersion())) }
      case _ => T.task { None }
    }
    T.command {
      def prettyDep(dep: Dep): String = {
        s"${dep.dep.module.orgName}:${dep.dep.version}"
      }
      val log = T.ctx().log

      val runClasspathIO =
        runClasspath().iterator.map(_.path).filter(os.exists).map(_.toIO).toSeq
      val current = mimaCurrentArtifact().path.pipe {
        case p if os.exists(p) => p
        case _                 => (T.dest / "emptyClasses").tap(os.makeDir)
      }.toIO

      val previous = resolvedMimaPreviousArtifacts().iterator.map {
        case (dep, artifact) =>
          worker.api.Artifact(prettyDep(dep), artifact.path.toIO)
      }.toSeq

      val checkDirection = mimaCheckDirection() match {
        case CheckDirection.Forward  => worker.api.CheckDirection.Forward
        case CheckDirection.Backward => worker.api.CheckDirection.Backward
        case CheckDirection.Both     => worker.api.CheckDirection.Both
      }

      def toWorkerApi(p: ProblemFilter) =
        worker.api.ProblemFilter(name = p.name, problem = p.problem)

      val incompatibleSignatureProblemFilters =
        if (mimaReportSignatureProblems()) Seq.empty
        else Seq(ProblemFilter.exclude[IncompatibleSignatureProblem]("*"))
      val binaryFilters =
        (mimaBinaryIssueFilters() ++ incompatibleSignatureProblemFilters)
          .map(toWorkerApi)
      val backwardFilters =
        mimaBackwardIssueFilters().view.mapValues(_.map(toWorkerApi)).toMap
      val forwardFilters =
        mimaForwardIssueFilters().view.mapValues(_.map(toWorkerApi)).toMap
      val publishVersion = publishDataTask().map(_._3)

      mimaWorker().reportBinaryIssues(
        scalaBinVersionTask(),
        log.debug(_),
        log.error(_),
        log.outputStream.println(_),
        checkDirection,
        runClasspathIO,
        previous,
        current,
        binaryFilters,
        backwardFilters,
        forwardFilters,
        mimaExcludeAnnotations(),
        publishVersion
      ) match {
        case Some(error) => Result.Failure(error)
        case None        => Result.Success(())
      }
    }
  }

}
