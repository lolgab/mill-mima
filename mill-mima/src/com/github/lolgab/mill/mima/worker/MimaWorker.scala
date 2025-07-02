package com.github.lolgab.mill.mima.worker

import com.github.lolgab.mill.mima.worker.api.MimaWorkerApi
import mill.PathRef
import mill.Task
import mill.api.Discover
import mill.api.TaskCtx

class MimaWorker {
  private var scalaInstanceCache = Option.empty[(Long, MimaWorkerApi)]

  def impl(mimaWorkerClasspath: Seq[PathRef]): MimaWorkerApi = {
    val classloaderSig = mimaWorkerClasspath.hashCode
    scalaInstanceCache match {
      case Some((sig, bridge)) if sig == classloaderSig => bridge
      case _                                            =>
        val cl = mill.util.Jvm.createClassLoader(
          mimaWorkerClasspath.map(_.path).toSeq,
          parent = getClass.getClassLoader,
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

object MimaWorkerExternalModule extends mill.api.ExternalModule {
  def mimaWorker: Task.Worker[MimaWorker] = Task.Worker {
    new MimaWorker()
  }
  lazy val millDiscover = Discover[this.type]
}
