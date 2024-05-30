package com.typesafe.tools.mima.core

private[com] object MyProblemReporting {
  def isReported(
      versionOpt: Option[String],
      filters: Seq[ProblemFilter],
      versionedFilters: Map[String, Seq[ProblemFilter]]
  )(problem: Problem): Boolean = {
    val versionMatchingFilters = versionOpt match {
      case None => Seq.empty
      case Some(version) =>
        versionedFilters
          // get all filters that apply to given module version or any version after it
          .collect {
            case (version2, filters)
                if ProblemReporting.versionOrdering.gteq(version2, version) =>
              filters
          }.flatten
    }

    (versionMatchingFilters.iterator ++ filters).forall(filter =>
      filter(problem)
    )
  }
}
