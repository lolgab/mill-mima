package com.github.lolgab.mill.mima.worker

import com.github.lolgab.mill.mima.worker.api._
import com.typesafe.tools.mima.core.MyProblemReporting
import com.typesafe.tools.mima.core.Problem
import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.core.{ProblemFilter => MimaProblemFilter}
import com.typesafe.tools.mima.lib.MiMaLib

class MimaWorkerImpl extends MimaWorkerApi {

  def reportBinaryIssues(
      scalaBinaryVersion: Option[String],
      logDebug: String => Unit,
      logError: String => Unit,
      logPrintln: String => Unit,
      checkDirection: CheckDirection,
      runClasspath: Seq[java.io.File],
      previous: Seq[Artifact],
      current: java.io.File,
      binaryFilters: Seq[ProblemFilter],
      backwardFilters: Map[String, Seq[ProblemFilter]],
      forwardFilters: Map[String, Seq[ProblemFilter]],
      excludeAnnos: Seq[String],
      publishVersion: Option[String]
  ): Option[String] = {
    sanityCheckScalaBinaryVersion(scalaBinaryVersion)

    val mimaLib = new MiMaLib(runClasspath.toSeq)

    def isReported(
        versionedFilters: Map[String, Seq[ProblemFilter]]
    )(problem: Problem) = {
      val filters = binaryFilters.map(problemFilterToMima)
      val mimaVersionedFilters = versionedFilters.map { case (k, v) =>
        k -> v.map(problemFilterToMima)
      }
      MyProblemReporting.isReported(
        publishVersion,
        filters,
        mimaVersionedFilters
      )(problem)
    }

    logPrintln(
      s"Scanning binary compatibility in ${current} ..."
    )
    val (problemsCount, filteredCount) =
      previous.foldLeft((0, 0)) { case ((totalAgg, filteredAgg), prev) =>
        def checkBC =
          mimaLib.collectProblems(prev.file, current, excludeAnnos.toList)

        def checkFC =
          mimaLib.collectProblems(current, prev.file, excludeAnnos.toList)

        val (backward, forward) = checkDirection match {
          case CheckDirection.Backward => (checkBC, Nil)
          case CheckDirection.Forward  => (Nil, checkFC)
          case CheckDirection.Both     => (checkBC, checkFC)
        }
        val backErrors = backward.filter(isReported(backwardFilters))
        val forwErrors = forward.filter(isReported(forwardFilters))
        val count = backErrors.size + forwErrors.size
        val filteredCount = backward.size + forward.size - count
        val doLog = if (count == 0) logDebug(_) else logError(_)
        doLog(s"Found ${count} issue when checking against ${prev.prettyDep}")
        backErrors.foreach(problem => doLog(prettyProblem("current")(problem)))
        forwErrors.foreach(problem => doLog(prettyProblem("other")(problem)))
        (totalAgg + count, filteredAgg + filteredCount)
      }

    if (problemsCount > 0) {
      val filteredNote =
        if (filteredCount > 0) s" (filtered $filteredCount)" else ""
      Some(
        s"Failed binary compatibility check! Found $problemsCount potential problems$filteredNote"
      )
    } else {
      logPrintln("Binary compatibility check passed")
      None
    }
  }

  private def prettyProblem(affected: String)(p: Problem): String = {
    val desc = p.description(affected)
    val howToFilter = p.howToFilter.fold("")(s =>
      s"\n   filter with: ${s.replace("ProblemFilters.exclude", ("ProblemFilter.exclude"))}"
    )
    s" * $desc$howToFilter"
  }

  private def sanityCheckScalaBinaryVersion(
      scalaBinaryVersion: Option[String]
  ) = {
    scalaBinaryVersion match {
      case Some("3" | "2.13" | "2.12" | "2.11") | None => // ok
      case Some(other) =>
        throw new IllegalArgumentException(
          s"MiMa supports Scala 2.11, 2.12, 2.13 and 3, not $other"
        )
    }
  }

  private def problemFilterToMima(
      problemFilter: ProblemFilter
  ): MimaProblemFilter =
    ProblemFilters.exclude(problemFilter.problem, problemFilter.name)

}
