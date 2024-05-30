package com.github.lolgab.mill.mima

import mill.Agg
import mill.PathRef
import mill.T
import mill.api.Result
import mill.define.Task
import mill.scalalib._

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
      deps: Task[Agg[Dep]]
  ): Task[Agg[(Dep, Agg[PathRef])]] = T.task {
    val pRepositories = repositoriesTask()
    val bind = bindDependency()
    val pDeps = deps()
    pDeps.map { dep =>
      val Result.Success(resolved) = Lib.resolveDependencies(
        repositories = pRepositories,
        deps = Agg(dep)
          .map(bind)
          .map(dep => dep.copy(dep = dep.dep.withTransitive(false))),
        ctx = Some(implicitly[mill.api.Ctx.Log])
      )
      (dep, resolved)
    }
  }
}
