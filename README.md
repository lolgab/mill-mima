# MiMa Mill Plugin

Port of the [MiMa Sbt Plugin](https://github.com/lightbend/mima)

## Getting Started

After importing it in the `build.sc` file:

```scala
import $ivy.`com.github.lolgab::mima_mill0.9:x.y.z`
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

Also the required direction of binary compatibility can be set by overriding `mimaCheckDirection`:

```scala
override def mimaCheckDirection = CheckDirection.Both
```

The possible directions are `Backward` (default), `Forward` and `Both`.
