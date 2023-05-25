package com.github.lolgab.mill.mima

import com.github.lolgab.mill.mima.worker.MimaBuildInfo
import mill._
import mill.main.BuildInfo
import mill.scalalib._

private[mima] trait VersionSpecific extends CoursierModule {
  private[mima] def mimaWorkerClasspath: T[Agg[PathRef]] = T {
    Lib
      .resolveDependencies(
        repositoriesTask(),
        Agg(
          ivy"com.github.lolgab:mill-mima-worker-impl_2.13:${MimaBuildInfo.publishVersion}"
            .exclude("com.github.lolgab" -> "mill-mima-worker-api_2.13")
        ).map(Lib.depToBoundDep(_, BuildInfo.scalaVersion)),
        ctx = Some(T.log)
      )
  }
}
