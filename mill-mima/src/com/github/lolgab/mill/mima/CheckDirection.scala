package com.github.lolgab.mill.mima

sealed trait CheckDirection
object CheckDirection {
  case object Backward extends CheckDirection
  case object Forward extends CheckDirection
  case object Both extends CheckDirection
}
