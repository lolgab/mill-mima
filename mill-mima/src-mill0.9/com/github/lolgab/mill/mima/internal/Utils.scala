package com.github.lolgab.mill.mima.internal

private[mima] object Utils {
  def scalaBinaryVersion(version: String): String = {
    mill.scalalib.api.Util.scalaBinaryVersion(version)
  }
}
