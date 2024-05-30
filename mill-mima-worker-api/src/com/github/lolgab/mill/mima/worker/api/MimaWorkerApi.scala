package com.github.lolgab.mill.mima.worker.api;

import java.io.File

trait MimaWorkerApi {
  def reportBinaryIssues(
      scalaBinaryVersion: Option[String],
      logDebug: String => Unit,
      logError: String => Unit,
      logPrintln: String => Unit,
      checkDirection: CheckDirection,
      runClasspath: Seq[File],
      artifacts: Seq[Artifact],
      current: File,
      binaryFilters: Seq[ProblemFilter],
      backwardFilters: Map[String, Seq[ProblemFilter]],
      forwardFilters: Map[String, Seq[ProblemFilter]],
      excludeAnnos: Seq[String],
      publishVersion: Option[String]
  ): Option[String]
}

case class Artifact(prettyDep: String, file: File)

sealed trait CheckDirection
object CheckDirection {
  case object Backward extends CheckDirection
  case object Forward extends CheckDirection
  case object Both extends CheckDirection
}

case class ProblemFilter(name: String, problem: String)
