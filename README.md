# MiMa Mill Plugin

Port of the [MiMa Sbt Plugin](https://github.com/lightbend/mima)

## Getting Started

After importing it in the `build.sc` file:

```scala
import $ivy.`com.github.lolgab::mill-mima_mill0.9:x.y.z`
import com.github.lolgab.mill.mima._
```

this plugin can be mixed in a `ScalaModule` defining the `mimaPreviousArtifacts`:

```scala
object module extends ScalaModule with Mima {
  def scalaVersion = "2.13.4"
  def mimaPreviousArtifacts = Agg(
    ivy"my_group_id::module:my_previous_version"
  )
}
```

## Configuration

### mimaCheckDirection

The required direction of binary compatibility can be set by overriding `mimaCheckDirection`:

```scala
override def mimaCheckDirection = CheckDirection.Both
```

The possible directions are `Backward` (default), `Forward` and `Both`.

### mimaBinaryIssueFilters

When MiMa reports a binary incompatibility that you consider acceptable, such as a change in an internal package,
you need to use the `mimaBinaryIssueFilters` setting to filter it out and get `mimaReportBinaryIssues` to
pass, like so:

```scala
import com.typesafe.tools.mima.core._

object mylibrary extends ScalaModule with PublishModule with Mima {
  override def mimaBinaryIssueFilters = super.mimaBinaryIssueFilters() ++ Seq(
    ProblemFilters.exclude[MissingClassProblem]("com.example.mylibrary.internal.Foo")
  )

  // ... other settings
}
```

You may also use wildcards in the package and/or the top `Problem` parent type for such situations:

```scala
override def mimaBinaryIssueFilters = super.mimaBinaryIssueFilters() ++ Seq(
  ProblemFilters.exclude[MissingClassProblem]("com.example.mylibrary.internal.Foo")
)
```
