package com.github.lolgab.mill.mima
import mill.testkit.IntegrationTester
import utest._

object IntegrationTests extends TestSuite {
  val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
  val millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
  val tempDir = os.temp.dir()

  // publishLocal mill-mima on temporary ivy.home
  os.call(
    cmd = (
      "./mill",
      "--no-build-lock",
      "__.publishLocal",
      "--localIvyRepo",
      tempDir / "local"
    ),
    cwd = os.Path(sys.env("MILL_WORKSPACE_ROOT")),
    env = Map("MILL_MIMA_FORCED_VERSION" -> "TEST")
  )

  def tests: Tests = Tests {

    def buildTester(folder: String) = IntegrationTester(
      daemonMode = true,
      workspaceSourcePath = resourceFolder / folder,
      millExecutable = millExecutable
    )

    extension(tester: IntegrationTester) {
      def publishLocal(module: String) =
        tester.eval(
          (
            s"$module.publishLocal",
            "--localIvyRepo",
            tempDir / "local"
          )
        )
      def myEval(cmd: String) = tester.eval(cmd = (s"-ivy.home=${tempDir}", cmd))
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
    test("previous-versions") {
      val tester = buildTester("previous-versions")

      tester.publishLocal("prev")
      tester.publishLocal("prev.js")

      val res2 = tester.myEval("verify")
      assert(res2.isSuccess)

      val res3 = tester.myEval("verifyFail")
      assert(!res3.isSuccess)
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
