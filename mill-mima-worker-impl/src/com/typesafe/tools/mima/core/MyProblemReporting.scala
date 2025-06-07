package com.typesafe.tools.mima.core

private[com] object MyProblemReporting {
  def isReported(
      versionOpt: Option[String],
      filters: Seq[ProblemFilter],
      versionedFilters: Map[String, Seq[ProblemFilter]]
  )(problem: Problem): Boolean = versionOpt match {
    case None          => filters.forall(_(problem))
    case Some(version) =>
      ProblemReporting.isReported(version, filters, versionedFilters)(problem)
  }
}
