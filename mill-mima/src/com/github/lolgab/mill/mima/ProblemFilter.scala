package com.github.lolgab.mill.mima

import upickle.default.ReadWriter

import scala.reflect.ClassTag
import scala.reflect.classTag

class ProblemFilter private (val name: String, val problem: String)
object ProblemFilter {
  def exclude[P <: ProblemRef: ClassTag](name: String): ProblemFilter =
    new ProblemFilter(
      name = name,
      problem = classTag[P].runtimeClass.getSimpleName()
    )

  private case class ProblemFilterImpl(name: String,problem: String) derives ReadWriter

  given ReadWriter[ProblemFilter] = summon[ReadWriter[ProblemFilterImpl]].bimap(
        pf => ProblemFilterImpl(pf.name, pf.problem),
        pfi => new ProblemFilter(pfi.name, pfi.problem)
      )
}
