package com.github.lolgab.mill.mima

import upickle.default.*

enum CheckDirection derives ReadWriter {
  case Backward, Forward, Both
}
