import sbt.url

addCommandAlias("ci-js",       s";clean ;logbackJS/test")
addCommandAlias("ci-jvm",      s";clean ;logbackJVM/test")

inThisBuild(List(
  organization := "io.monix",
  homepage := Some(url("https://monix.io")),
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  developers := List(
    Developer(
      id="Avasil",
      name="Piotr Gawrys",
      email="pgawrys2@gmail.com",
      url=url("https://github.com/Avasil")
    ))
))

val monixVersion = "3.3.0"
val catsEffectVersion = "2.1.4"

lazy val `monix-mdc` = project.in(file("."))
  .settings(skipOnPublishSettings)
  .aggregate(rootJVM, rootJS)
  .settings(sharedSettings)

lazy val rootJVM = project
  .aggregate(logback.jvm)
  .settings(skipOnPublishSettings)

lazy val rootJS = project
  .aggregate(logback.js)
  .settings(skipOnPublishSettings)

lazy val logback = crossProject(JSPlatform, JVMPlatform)
  .in(file("logback"))
  .settings(sharedSettings)
  .settings(
    name := "monix-mdc-logback",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic"  % "1.2.3",
      "org.scalatest"  %% "scalatest"       % "3.1.0" % Test,
      "org.scalacheck" %% "scalacheck"      % "1.14.0" % Test,
      "io.monix"       %% "monix-eval"           % monixVersion % Test,
    )
  )
  .enablePlugins(AutomateHeaderPlugin)


lazy val contributors = Seq(
  "Avasil" -> "Piotr Gawrys"
)

lazy val doNotPublishArtifact = Seq(
  publishArtifact := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageBin) := false
)

// General Settings
lazy val sharedSettings = Seq(
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.12.10", "2.13.1"),
  scalacOptions ++= Seq(
    // warnings
    "-unchecked", // able additional warnings where generated code depends on assumptions
    "-deprecation", // emit warning for usages of deprecated APIs
    "-feature", // emit warning usages of features that should be imported explicitly
    // Features enabled by default
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:experimental.macros",
  ),

  // Linter
  scalacOptions ++= Seq(
    // Turns all warnings into errors ;-)
    //    "-Xfatal-warnings",
    // Enables linter options
    "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
    "-Xlint:nullary-unit", // warn when nullary methods return Unit
    "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
    "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
    "-Xlint:missing-interpolator", // a string literal appears to be missing an interpolator id
    "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
    "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
    "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
    "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
    "-Xlint:option-implicit", // Option.apply used implicit view
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit
    "-Xlint:package-object-classes", // Class or object defined in package object
  ),

  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),

  libraryDependencies ++= Seq(
    "io.monix" %%% "monix-execution" % monixVersion
  ),

  scalacOptions in ThisBuild ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath", file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),

  resolvers ++= Seq(
    "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases",
    Resolver.sonatypeRepo("releases")
  ),

  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  // -- Settings meant for deployment on oss.sonatype.org
  sonatypeProfileName := organization.value,

  isSnapshot := version.value endsWith "SNAPSHOT",
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  headerLicense := Some(HeaderLicense.Custom(
    """|Copyright (c) 2021-2021 by The Monix Project Developers.
       |See the project homepage at: https://monix.io
       |
       |Licensed under the Apache License, Version 2.0 (the "License");
       |you may not use this file except in compliance with the License.
       |You may obtain a copy of the License at
       |
       |    http://www.apache.org/licenses/LICENSE-2.0
       |
       |Unless required by applicable law or agreed to in writing, software
       |distributed under the License is distributed on an "AS IS" BASIS,
       |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       |See the License for the specific language governing permissions and
       |limitations under the License."""
      .stripMargin)),

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/monix/monix-mdc"),
      "scm:git@github.com:monix/monix-mdc.git"
    )),
)


lazy val skipOnPublishSettings = Seq(
  skip in publish := true,
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publishTo := None
)

/* The BaseVersion setting represents the in-development (upcoming) version,
 * as an alternative to SNAPSHOTS.
 */
git.baseVersion := (version in ThisBuild).value
