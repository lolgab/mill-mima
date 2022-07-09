# MiMa Mill Plugin

Port of the [MiMa Sbt Plugin](https://github.com/lightbend/mima)

## Getting Started

After importing it in the `build.sc` file:

```scala
import $ivy.`com.github.lolgab::mill-mima::x.y.z`
import com.github.lolgab.mill.mima._
```

this plugin can be mixed in a `ScalaModule with PublishModule` defining the `mimaPreviousVersions`:

```scala
object module extends ScalaModule with PublishModule with Mima {
  def mimaPreviousVersions = Seq("1.0.0", "1.5.0")

  // ... other settings
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
import com.github.lolgab.mill.mima._

object mylibrary extends ScalaModule with PublishModule with Mima {
  override def mimaBinaryIssueFilters = super.mimaBinaryIssueFilters() ++ Seq(
    ProblemFilter.exclude[MissingClassProblem]("com.example.mylibrary.internal.Foo")
  )

  // ... other settings
}
```

You may also use wildcards in the package and/or the top `Problem` parent type for such situations:

```scala
import com.github.lolgab.mill.mima._

override def mimaBinaryIssueFilters = super.mimaBinaryIssueFilters() ++ Seq(
  ProblemFilter.exclude[MissingClassProblem]("com.example.mylibrary.internal.*")
)
```

### mimaExcludeAnnotations

The fully-qualified class names of annotations that exclude parts of the API from problem checking.

```scala
import com.github.lolgab.mill.mima._

object mylibrary extends ScalaModule with PublishModule with Mima {
  override def mimaExcludeAnnotations = Seq(
    Seq("mima.annotation.exclude")
  )
  // ... other settings
}
```

### mimaPreviousArtifacts

If your previous artifacts have a different `groupId` or `artifactId` you can check against them
using `mimaPreviousArtifacts` instead of `millPreviousVersions` (since `millPreviousVersions`
assumes the same `groupId` and `artifactId`):

```scala
def mimaPreviousArtifacts = Agg(
  ivy"my_group_id::module:my_previous_version"
)
```

### mimaBackwardIssueFilters

Filters to apply to binary issues found grouped by version of a module
checked against. These filters only apply to backward compatibility
checking.

Signature: 

```scala
def mimaBackwardIssueFilters: Target[Map[String, Seq[ProblemFilter]]]
```

### mimaForwardIssueFilters

Filters to apply to binary issues found grouped by version of a module
checked against. These filters only apply to forward compatibility
checking.

Signature: 

```scala
def mimaForwardIssueFilters: Target[Map[String, Seq[ProblemFilter]]]
```

### IncompatibleSignatureProblem

Most MiMa checks (`DirectMissingMethod`,`IncompatibleResultType`, `IncompatibleMethType`, etc) are against the "method descriptor", which is the "raw" type signature, without any information about generic parameters.

The `IncompatibleSignature` check compares the `Signature`, which includes the full signature including generic parameters. This can catch real incompatibilities, but also sometimes triggers for a change in generics that would not in fact cause problems at run time. Notably, it will warn when updating your project to scala 2.12.9+ or 2.13.1+, see [this issue](https://github.com/lightbend/mima/issues/423) for details.

You can opt-in to this check by setting:

```scala
def mimaReportSignatureProblems = true
```

## Changelog

### 0.0.11

- Add support for `mimaReportSignatureProblems`

### 0.0.10

- Run Mima in a separate classloader.
  Now `Problem`s are mirrored in the `com.github.lolgab.mill.mima` package
  instead and the `com.typesafe.tools.mima.core` package

### 0.0.9

- Support Mill 0.10

### 0.0.8

- Correct hint in error message to match plugin's ProblemFilter class

### 0.0.7

- Support Mill 0.10.0-M5

### 0.0.6

- Support Mill 0.10.0-M4

### 0.0.5

- Add `mimaExcludeAnnotations` target
- Bump MiMa to `1.0.1`
- Fix `mill-scalalib` dependency to be in `compileIvyDeps`

### 0.0.4

- Add support to resolve multiple previous artifacts
- Add `mimaPreviousVersions` target
- Redefine `prepareOffline` to include MiMa artifacts

### 0.0.3

- Support problem filters

### 0.0.2

- Change artifact name from `mima_mill0.9` to `mill-mima_mill0.9`

### 0.0.1

First release
