package com.github.lolgab.mill.mima

import scala.util.chaining._

import com.github.lolgab.mill.mima.internal.Utils.scalaBinaryVersion
import com.typesafe.tools.mima.core.MyProblemReporting
import com.typesafe.tools.mima.core.Problem
import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.core.{ProblemFilter => MimaProblemFilter}
import com.typesafe.tools.mima.lib.MiMaLib
import mill._
import mill.api.Result
import mill.define.Command
import mill.define.Target
import mill.scalalib._

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

  def mimaReportBinaryIssues(): Command[Unit] = T.command {
    sanityCheckScalaVersion(scalaVersion())
    val log = T.ctx().log
    val mimaLib =
      new MiMaLib(runClasspath().map(_.path).filter(os.exists).map(_.toIO))

    val classes = compile().classes.path.pipe {
      case p if os.exists(p) => p
      case _                 => (T.dest / "emptyClasses").tap(os.makeDir)
    }

    def isReported(
        versionedFilters: Map[String, Seq[ProblemFilter]]
    )(problem: Problem) = {
      val filters = mimaBinaryIssueFilters().map(problemFilterToMima)
      val mimaVersionedFilters = versionedFilters.map { case (k, v) =>
        k -> v.map(problemFilterToMima)
      }
      MyProblemReporting.isReported(
        publishVersion(),
        filters,
        mimaVersionedFilters
      )(problem)
    }

    val backwardFilters = mimaBackwardIssueFilters()
    val forwardFilters = mimaForwardIssueFilters()
    val excludeAnnos = mimaExcludeAnnotations().toList

    log.outputStream.println(
      s"Scanning binary compatibility in ${classes} ..."
    )
    val (problemsCount, filteredCount) =
      resolvedMimaPreviousArtifacts().iterator.foldLeft((0, 0)) {
        case ((totalAgg, filteredAgg), (dep, artifact)) =>
          val prev = artifact.path.toIO
          val curr = classes.toIO

          def checkBC = mimaLib.collectProblems(prev, curr, excludeAnnos)

          def checkFC = mimaLib.collectProblems(curr, prev, excludeAnnos)

          val (backward, forward) = mimaCheckDirection() match {
            case CheckDirection.Backward => (checkBC, Nil)
            case CheckDirection.Forward  => (Nil, checkFC)
            case CheckDirection.Both     => (checkBC, checkFC)
          }
          val backErrors = backward.filter(isReported(backwardFilters))
          val forwErrors = forward.filter(isReported(forwardFilters))
          val count = backErrors.size + forwErrors.size
          val filteredCount = backward.size + forward.size - count
          val doLog = if (count == 0) log.debug(_) else log.error(_)
          doLog(s"Found ${count} issue when checking against ${prettyDep(dep)}")
          backErrors.foreach(problem =>
            doLog(prettyProblem("current")(problem))
          )
          forwErrors.foreach(problem => doLog(prettyProblem("other")(problem)))
          (totalAgg + count, filteredAgg + filteredCount)
      }

    if (problemsCount > 0) {
      val filteredNote =
        if (filteredCount > 0) s" (filtered $filteredCount)" else ""
      Result.Failure(
        s"Failed binary compatibility check! Found $problemsCount potential problems$filteredNote"
      )
    } else {
      log.outputStream.println("Binary compatibility check passed")
      Result.Success(())
    }
  }

  private def prettyDep(dep: Dep): String = {
    s"${dep.dep.module.orgName}:${dep.dep.version}"
  }

  private def prettyProblem(affected: String)(p: Problem): String = {
    val desc = p.description(affected)
    val howToFilter = p.howToFilter.fold("")(s =>
      s"\n   filter with: ${s.replace("ProblemFilters.exclude", ("ProblemFilter.exclude"))}"
    )
    s" * $desc$howToFilter"
  }

  private def sanityCheckScalaVersion(scalaVersion: String) = {
    scalaBinaryVersion(scalaVersion) match {
      case "2.11" | "2.12" | "2.13" | "3" => // ok
      case scalaVersion =>
        throw new IllegalArgumentException(
          s"MiMa supports Scala 2.11, 2.12, 2.13 and 3, not $scalaVersion"
        )
    }
  }

  private def problemFilterToMima(
      problemFilter: ProblemFilter
  ): MimaProblemFilter =
    ProblemFilters.exclude(problemFilter.problem, problemFilter.name)

}
