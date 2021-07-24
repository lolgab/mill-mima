package com.github.lolgab.mill.mima

import coursier.Dependency
import mill.Agg
import mill.PathRef
import mill.T
import mill.api.Result
import mill.define.Task
import mill.scalalib.CoursierModule
import mill.scalalib.Dep
import mill.scalalib.Lib

private[mima] trait ExtraCoursierSupport extends CoursierModule {

  /** Resolves each dependency independently.
    *
    * @param deps
    *   The dependencies to resolve.
    * @param sources
    *   If `true`, resolved the source jars instead of the binary jars.
    * @return
    *   Tuples containing each dependencies and it's resolved transitive
    *   artifacts.
    */
  protected def resolveSeparateNonTransitiveDeps(
      deps: Task[Agg[Dep]],
      sources: Boolean = false
  ): Task[Agg[(Dep, Agg[PathRef])]] = T.task {
    val pRepositories = repositoriesTask()
    val pDepToDependency =
      resolveCoursierDependency().apply(_).withTransitive(false)
    val pDeps = deps()
    val pMapDeps = mapDependencies()
    // API only available for mill 0.10 line
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
}
