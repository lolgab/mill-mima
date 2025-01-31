package com.github.lolgab.mill.mima
import mill.testkit.IntegrationTester
import utest._

object IntegrationTests extends TestSuite {
  def tests: Tests = Tests {
    val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
    val millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))

    test("simple") {
      val tester = new IntegrationTester(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "simple",
        millExecutable = millExecutable
      )

      val res1 = tester.eval("prepare")
      assert(res1.isSuccess)
      
      val res2 = tester.eval("verify")
      assert(!res2.isSuccess)
    }
    test("filters") {
      val tester = new IntegrationTester(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "filters",
        millExecutable = millExecutable
      )

      val res1 = tester.eval("prepare")
      assert(res1.isSuccess)
      
      val res2 = tester.eval("verify")
      assert(!res2.isSuccess)
    }
    test("previous-versions") {
      val tester = new IntegrationTester(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "previous-versions",
        millExecutable = millExecutable
      )

      val res1 = tester.eval("prepare")
      assert(res1.isSuccess)
      
      val res2 = tester.eval("verify")
      assert(res2.isSuccess)

      val res3 = tester.eval("verifyFail")
      assert(!res3.isSuccess)

      val res4 = tester.eval("verifyFailJs")
      assert(!res4.isSuccess)
    }
    test("java") {
      val tester = new IntegrationTester(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "java",
        millExecutable = millExecutable
      )

      val res1 = tester.eval("prepare")
      assert(res1.isSuccess)
      
      val res2 = tester.eval("verify")
      assert(!res2.isSuccess)
    }
    test("not-publish-module") {
      val tester = new IntegrationTester(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "not-publish-module",
        millExecutable = millExecutable
      )

      val res1 = tester.eval("prepare")
      assert(res1.isSuccess)
      
      val res2 = tester.eval("verify")
      assert(!res2.isSuccess)
    }
    test("mima-version") {
      val tester = new IntegrationTester(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "mima-version",
        millExecutable = millExecutable
      )

      val res1 = tester.eval("prepare")
      assert(res1.isSuccess)
      
      val res2 = tester.eval("verify")
      assert(!res2.isSuccess)
    }
  }
}