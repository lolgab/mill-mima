package com.github.lolgab.mill.mima.worker

import com.github.lolgab.mill.mima.worker.api.MimaWorkerApi
import mill.Agg
import mill.T
import mill.define.Discover
import mill.define.Worker

class MimaWorker {
  private var scalaInstanceCache = Option.empty[(Long, MimaWorkerApi)]

  def impl(
      mimaWorkerClasspath: Agg[os.Path]
  )(implicit ctx: mill.api.Ctx.Home): MimaWorkerApi = {
    val classloaderSig = mimaWorkerClasspath
      .map(p => p.toString().hashCode + os.mtime(p))
      .iterator
      .sum
    scalaInstanceCache match {
      case Some((sig, bridge)) if sig == classloaderSig => bridge
      case _ =>
        val cl = mill.api.ClassLoader.create(
          mimaWorkerClasspath.map(_.toIO.toURI.toURL).iterator.to(Seq),
          parent = null,
          sharedLoader = getClass.getClassLoader,
          sharedPrefixes = Seq("com.github.lolgab.mill.mima.worker.api.")
        )
        try {
          val bridge = cl
            .loadClass(
              "com.github.lolgab.mill.mima.worker.MimaWorkerImpl"
            )
            .getDeclaredConstructor()
            .newInstance()
            .asInstanceOf[
              com.github.lolgab.mill.mima.worker.api.MimaWorkerApi
            ]
          scalaInstanceCache = Some((classloaderSig, bridge))
          bridge
        } catch {
          case e: Exception =>
            e.printStackTrace()
            throw e
        }
    }
  }
}

object MimaWorkerExternalModule extends mill.define.ExternalModule {
  def mimaWorker: Worker[MimaWorker] = T.worker {
    new MimaWorker()
  }
  lazy val millDiscover = Discover[this.type]
}
