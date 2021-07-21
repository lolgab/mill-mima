package com.github.lolgab.mill.mima

import scala.util.chaining._

import com.typesafe.tools.mima.core.MyProblemReporting
import com.typesafe.tools.mima.core.Problem
import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.core.{ProblemFilter => MimaProblemFilter}
import com.typesafe.tools.mima.lib.MiMaLib
import mill._
import mill.api.Result
import mill.define.Command
import mill.define.Target
import mill.define.Task
import mill.scalalib._
import mill.scalalib.api.Util.scalaBinaryVersion

trait Mima extends ScalaModule with PublishModule {

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

  protected def resolveSeparateDeps(
      deps: Task[Agg[Dep]],
      sources: Boolean = false
  ): Task[Agg[(Dep, Agg[PathRef])]] = T.task {
    val pRepositories = repositoriesTask()
    val pDepToDependency = resolveCoursierDependency().apply(_)
    val pDeps = deps()
    val pMapDeps = mapDependencies()
    //    val pCustomizer = resolutionCustomizer()
    pDeps.map { dep =>
      val Result.Success(resolved) = Lib.resolveDependencies(
        repositories = pRepositories,
        depToDependency = pDepToDependency,
        deps = Agg(dep),
        sources = sources,
        mapDependencies = Some(pMapDeps),
        //        customizer = pCustomizer,
        ctx = Some(implicitly[mill.api.Ctx.Log])
      )
      (dep, resolved)
    }
  }

  private def resolvedMimaPreviousArtifacts: T[Agg[(Dep, PathRef)]] = T {
    Agg.from(
      resolveSeparateDeps(mimaPreviousArtifacts)().toSeq.map(p =>
        p._1.exclude("*" -> "*") -> p._2.iterator.next
      )
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

    log.outputStream.println(
      s"Scanning binary compatibility in ${classes} ..."
    )
    val (problemsCount, filteredCount) =
      resolvedMimaPreviousArtifacts().iterator.foldLeft((0, 0)) {
        case ((totalAgg, filteredAgg), (dep, artifact)) =>
          val prev = artifact.path.toIO
          val curr = classes.toIO

          def checkBC = mimaLib.collectProblems(prev, curr)

          def checkFC = mimaLib.collectProblems(curr, prev)

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
          backErrors.foreach(problem => doLog(pretty("current")(problem)))
          forwErrors.foreach(problem => doLog(pretty("other")(problem)))
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

  private def pretty(affected: String)(p: Problem): String = {
    val desc = p.description(affected)
    val howToFilter = p.howToFilter.fold("")(s => s"\n   filter with: $s")
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
