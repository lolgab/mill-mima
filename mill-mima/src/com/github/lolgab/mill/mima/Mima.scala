package com.github.lolgab.mill.mima

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.lib.MiMaLib
import mill._
import mill.api.Result
import mill.define.Command
import mill.define.Target
import mill.scalalib._
import mill.scalalib.api.Util.scalaBinaryVersion

trait Mima extends ScalaModule {

  def mimaPreviousArtifacts: Target[Agg[Dep]]

  def mimaCheckDirection: Target[CheckDirection] = T { CheckDirection.Backward }

  def mimaReportBinaryIssues(): Command[Unit] = T.command {
    sanityCheckScalaVersion(scalaVersion())
    val log = T.ctx().log
    val mimaLib = new MiMaLib(runClasspath().map(_.path).filter(os.exists).map(_.toIO))
    val resolvedMimaPreviousArtifacts =
      resolveDeps(T.task {
        mimaPreviousArtifacts().map(_.exclude("*" -> "*"))
      })()
    val classes = compile().classes.path // .toIO
    val classesCount = if(os.exists(classes)) os.walk.stream(classes).count() else 0

    if(classesCount == 0) {
      log.outputStream.println(s"No classfiles found for binary compatibility check in ${classes}")
      Result.Success(())
    } else {
      log.outputStream.println(s"Scanning ${classesCount} classfiles for binary compatibility in ${classes} ...")
      val problemsCount = resolvedMimaPreviousArtifacts.iterator.foldLeft(0) {
        (agg, artifact) =>
          val prev = artifact.path.toIO
          val curr = classes.toIO

          def checkBC = mimaLib.collectProblems(prev, curr)

          def checkFC = mimaLib.collectProblems(curr, prev)

          val (backwardProblems, forwardProblems) = mimaCheckDirection() match {
            case CheckDirection.Backward => (checkBC, Nil)
            case CheckDirection.Forward => (Nil, checkFC)
            case CheckDirection.Both => (checkBC, checkFC)
          }
          val count = backwardProblems.length + forwardProblems.length
          val doLog = if (count == 0) log.debug(_) else log.error(_)
          backwardProblems.foreach(problem => doLog(pretty("current")(problem)))
          forwardProblems.foreach(problem => doLog(pretty("other")(problem)))
          agg + count
      }

      if (problemsCount > 0) {
        Result.Failure(
          s"Failed binary compatibility check! Found $problemsCount potential problems."
        )
      } else {
        log.outputStream.println("Binary compatibility check passed")
        Result.Success(())
      }
    }
  }

  private def pretty(affected: String)(p: Problem): String = {
    val desc = p.description(affected)
    val howToFilter = p.howToFilter.fold("")(s => s"\n   filter with: $s")
    s" * $desc$howToFilter"
  }

  private def sanityCheckScalaVersion(scalaVersion: String) = {
    scalaBinaryVersion(scalaVersion) match {
      case "2.11" | "2.12" | "2.13" | "3" => // ok
      case scalaVersion =>
        throw new IllegalArgumentException(
          s"MiMa supports Scala 2.11, 2.12, 2.13 and 3, not $scalaVersion"
        )
    }
  }
}
