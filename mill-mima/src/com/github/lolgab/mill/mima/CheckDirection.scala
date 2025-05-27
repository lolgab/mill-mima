package com.github.lolgab.mill.mima

import upickle.default._

enum CheckDirection derives ReadWriter {
  case Backward, Forward, Both
}
