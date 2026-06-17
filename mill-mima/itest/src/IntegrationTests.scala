package com.github.lolgab.mill.mima
import mill.testkit.IntegrationTester
import utest._

object IntegrationTests extends TestSuite {
  val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
  val millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
  val testVersion = sys.env("MILL_MIMA_TEST_VERSION")
  val tempDir = os.temp.dir()

  def tests: Tests = Tests {

    def buildTester(folder: String) = {
      val tester = IntegrationTester(
        daemonMode = false,
        workspaceSourcePath = resourceFolder / folder,
        millExecutable = millExecutable
      )
      tester.modifyFile(
        tester.workspacePath / "build.mill",
        _.replace("::TEST", s"::$testVersion")
      )
      tester
    }

    extension (tester: IntegrationTester) {
      def publishLocal(module: String) =
        tester.eval(
          (
            s"$module.publishLocal",
            "--localIvyRepo",
            tempDir / "local"
          )
        )
      def myEval(cmd: String) =
        tester.eval(cmd = ("--define", s"ivy.home=${tempDir}", cmd))
    }

    test("simple") {
      val tester = buildTester("simple")

      val res1 = tester.publishLocal("prev")
      assert(res1.isSuccess)

      val res2 = tester.myEval("curr.mimaReportBinaryIssues")
      val stderr: String = res2.err
      assert(!res2.isSuccess)
      assert(
        stderr.contains(
          "method hello()java.lang.String in object Main does not have a correspondent in current version"
        )
      )
    }
    test("filters") {
      val tester = buildTester("filters")

      val res1 = tester.publishLocal("prev")
      assert(res1.isSuccess)

      val res2 = tester.myEval("curr.mimaReportBinaryIssues")
      assert(res2.isSuccess)
    }
    test("version-filters") {
      val tester = buildTester("version-filters")

      assert(tester.publishLocal("prev").isSuccess)
      assert(tester.publishLocal("prev03").isSuccess)

      val passAgainstOld =
        tester.myEval("verifyPassesAgainstOldVersion")
      assert(passAgainstOld.isSuccess)

      val failAgainstNew =
        tester.myEval("verifyFailsAgainstNewVersion")
      assert(!failAgainstNew.isSuccess)
    }
    test("previous-versions") {
      val tester = buildTester("previous-versions")

      tester.publishLocal("prev")
      tester.publishLocal("prev.js")

      val res2 = tester.myEval("verify")
      assert(res2.isSuccess)

      val res3 = tester.myEval("verifyFail")
      assert(!res3.isSuccess)

      val res4 = tester.myEval("verifyFailJs")
      assert(!res4.isSuccess)
    }
    test("java") {
      val tester = buildTester("java")

      val res1 = tester.publishLocal("prev")
      assert(res1.isSuccess)

      val res2 = tester.myEval("curr.mimaReportBinaryIssues")
      assert(!res2.isSuccess)
      assert(
        res2.err.contains(
          "static method hello()java.lang.String in class Main does not have a correspondent in current version"
        )
      )
    }
    test("not-publish-module") {
      val tester = buildTester("not-publish-module")

      val res1 = tester.publishLocal("prev")
      assert(res1.isSuccess)

      val res2 = tester.myEval("verify")
      assert(!res2.isSuccess)
      assert(
        res2.err.contains(
          "method hello()java.lang.String in object Main does not have a correspondent in current version"
        )
      )
    }
    test("mima-version") {
      val tester = buildTester("mima-version")

      val res1 = tester.publishLocal("prev")
      assert(res1.isSuccess)

      val res2 = tester.myEval("curr.mimaReportBinaryIssues")
      assert(!res2.isSuccess)
      assert(
        res2.err.contains(
          "method hello()java.lang.String in object Main does not have a correspondent in current version"
        )
      )
    }
  }
}
