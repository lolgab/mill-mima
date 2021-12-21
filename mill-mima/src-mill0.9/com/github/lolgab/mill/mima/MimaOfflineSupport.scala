package com.github.lolgab.mill.mima

import mill._
import mill.define.Command
import mill.scalalib._

private[mima] trait MimaOfflineSupport
    extends MimaBase
    with OfflineSupportModule {

  override def prepareOffline(): Command[Unit] = T.command {
    super.prepareOffline()()
    resolvedMimaPreviousArtifacts()
    ()
  }

}
