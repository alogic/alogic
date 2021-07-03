////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// About the project
////////////////////////////////////////////////////////////////////////////////

name := "alogic"

organization := "com.argondesign"

////////////////////////////////////////////////////////////////////////////////
// Scala compiler
////////////////////////////////////////////////////////////////////////////////

scalaVersion := "2.13.6"

crossScalaVersions := Seq(scalaVersion.value, "3.0.0")

scalacOptions ++= Seq("-feature", "-unchecked")

scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, n)) => Seq("-explain", "-explain-types", "-source:3.0")
    case _            => Seq("-explaintypes", "-Xlint:_")
  }
}

// Note: Needed for Scala 3 Dokka dependencies, which are planned to be
// removed at some point. Remove when that happens
ThisBuild / resolvers += Resolver.JCenterRepository

////////////////////////////////////////////////////////////////////////////////
// Some of the build is conditional and built only on Java 11
////////////////////////////////////////////////////////////////////////////////

val fromJava11 = System.getProperty("java.version").takeWhile(_ != '.').toInt >= 11

unmanagedSources / excludeFilter := {
  if (fromJava11) {
    ""
  } else {
    new SimpleFileFilter(_.getCanonicalFile.toPath.getParent endsWith "gcp")
  }
}

////////////////////////////////////////////////////////////////////////////////
// Library dependencies
////////////////////////////////////////////////////////////////////////////////

libraryDependencies += "org.rogach" %% "scallop" % "4.0.3"

libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
) map {
  _ % "0.14.1"
}

// Java 11 only
libraryDependencies ++= {
  if (fromJava11) {
    Seq("com.google.cloud.functions" % "functions-framework-api" % "1.0.3")
  } else {
    Seq.empty
  }
}

////////////////////////////////////////////////////////////////////////////////
// Testing dependencies
////////////////////////////////////////////////////////////////////////////////

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % Test

libraryDependencies += "org.mockito" % "mockito-core" % "3.7.7" % Test

Test / logBuffered := false

Test / testOptions += Tests.Argument("-oD") // Add F for full stack traces

////////////////////////////////////////////////////////////////////////////////
// Target aliases
////////////////////////////////////////////////////////////////////////////////

addCommandAlias(
  "runUnitTests",
  "testOnly -- -l EndToEnd -l EndToEndParallel"
)

addCommandAlias(
  "runEndToEndTests",
  "testOnly -- -n EndToEnd"
)

addCommandAlias(
  "runEndToEndParallelTests",
  "testOnly -- -n EndToEndParallel"
)

////////////////////////////////////////////////////////////////////////////////
// Antlr4 plugin
////////////////////////////////////////////////////////////////////////////////

enablePlugins(Antlr4Plugin)

Antlr4 / antlr4Version := "4.9.0"

Antlr4 / antlr4Dependency := "com.tunnelvisionlabs" % "antlr4" % (Antlr4 / antlr4Version).value

Antlr4 / antlr4RuntimeDependency := "com.tunnelvisionlabs" % "antlr4-runtime" % (Antlr4 / antlr4Version).value

Antlr4 / antlr4PackageName := Some("com.argondesign.alogic.antlr")

Antlr4 / antlr4GenListener := false

Antlr4 / antlr4GenVisitor := true

////////////////////////////////////////////////////////////////////////////////
// SBT native packager
////////////////////////////////////////////////////////////////////////////////

enablePlugins(JavaAppPackaging)

bashScriptExtraDefines +=
  """
# Pass a secret option if stderr is tty, but only if not asking for
# --help or --version
if [[ "$*" != "-h" && "$*" != "--help" ]] && \
   [[ "$*" != "-v" && "$*" != "--version" ]] && \
   [[ -t 2 ]]; then
  stderrisatty="--stderrisatty"
fi

if [[ "$*" == "--compiler-deps" ]]; then
  readlink -f "$0"
  echo "$app_classpath" | tr ":" "\n"
  exit 0
fi

# Prepend '--' to the command line arguments. This in fact causes the wrapper
# to not consume any arguments like -h or -v.
set -- -- ${stderrisatty} "$@"

# Set Scala global concurrent thread pool size
setScalaParallelism() {
  intRe='^-?[0-9]+$'
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -j|--parallelism)
        ! [[ "$2" =~ $intRe ]] || echo "-Dscala.concurrent.context.maxThreads=$2"
        shift ;;
      *) shift ;;
    esac
  done
}
set -- $(setScalaParallelism "$@") "$@"

# Add JVM options
set -- -XX:+UseParallelGC "$@"
"""

////////////////////////////////////////////////////////////////////////////////
// SBT git
////////////////////////////////////////////////////////////////////////////////

enablePlugins(GitVersioning)

git.useGitDescribe := true

////////////////////////////////////////////////////////////////////////////////
// SBT buildinfo
////////////////////////////////////////////////////////////////////////////////

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](
  name,
  version,
  scalaVersion,
  sbtVersion,
  coverageEnabled,
  BuildInfoKey.action("buildTime")(System.currentTimeMillis)
)

buildInfoPackage := "com.argondesign.alogic"

////////////////////////////////////////////////////////////////////////////////
// SBT scoverage
////////////////////////////////////////////////////////////////////////////////

coverageOutputXML := true
coverageOutputHTML := !(sys.env.get("GITHUB_ACTIONS") contains "true")
coverageOutputCobertura := false

val coverageUpdateIgnored = taskKey[Unit]("Mark ignored statements as such")
coverageUpdateIgnored := {
  import scoverage.Serializer
  import scoverage.Coverage
  import scoverage.Statement

  // Load coverage
  val dataDir = crossTarget.value / "scoverage-data"
  val coverageFile = Serializer.coverageFile(dataDir)
  val coverage = Serializer.deserialize(coverageFile)

  // Gather code ranges that should be ignored
  val ignoreRanges = coverage.statements filter { stmt =>
    stmt.treeName == "Apply" && {
      // Arguments to assert/require (strictly, should ignore only the 2nd argument)
      stmt.symbolName == "scala.Predef.assert" ||
      stmt.symbolName == "scala.Predef.require"
    }
  } groupBy {
    _.location.fullClassName
  } mapValues {
    // Note start + 1 to ensure range does not include this stmt itself
    _.map(stmt => Range(stmt.start + 1, stmt.end)).toSet
  }

  // Predicate for ignored statements
  def isIgnored(stmt: Statement): Boolean = {
    def isAssertArgument(stmt: Statement): Boolean =
      ignoreRanges.get(stmt.location.fullClassName) match {
        case None => false
        case Some(set) =>
          set exists { range => (range contains stmt.start) && (range contains stmt.end) }
      }
    stmt.symbolName == "com.argondesign.alogic.util.unreachable" ||
    stmt.symbolName == "com.argondesign.alogic.core.Messaging.ice" ||
    stmt.desc.startsWith("throw com.argondesign.alogic.core.Messages.Ice") ||
    isAssertArgument(stmt)
  }

  // Compute updated coverage by marking ignored statements as such
  val updatedCoverage = Coverage()
  coverage.statements.iterator map {
    case stmt if isIgnored(stmt) => stmt.copy(ignored = true)
    case stmt                    => stmt
  } foreach updatedCoverage.add

  // Save updated coverage
  Serializer.serialize(updatedCoverage, coverageFile)
}

// UpdateIgnored before Report
coverageReport := (coverageReport dependsOn coverageUpdateIgnored).value

////////////////////////////////////////////////////////////////////////////////
// SBT assembly
////////////////////////////////////////////////////////////////////////////////

// Do not run tests when running assembly
assembly / test := {}

// Put output in it's own directory as needed by GCP
assembly / assemblyOutputPath := crossTarget.value / "assembly" / (assembly / assemblyJarName).value

////////////////////////////////////////////////////////////////////////////////
// Google Cloud Functions
////////////////////////////////////////////////////////////////////////////////

val gcfRun = taskKey[Unit]("Run local server for Google Cloud Function endpoint")
gcfRun := {
  import scala.collection.mutable
  import com.google.cloud.functions.invoker.runner.Invoker

  val log = streams.value.log
  val classPath = (Runtime / fullClasspath).value map { _.data } mkString ":"

  val args = new mutable.ArrayBuffer[String]
  args.append("--classpath")
  args.append(classPath)
  args.append("--target")
  args.append("com.argondesign.alogic.gcp.FunctionCompile")
  log.info("Calling Invoker with " + args);
  Invoker.main(args.toArray)
}

val gcfDeploy = taskKey[Unit]("Deploy Google Cloud Functions to GCP")
gcfDeploy := {
  import scala.sys.process._

  val log = streams.value.log

  Seq(
    "gcloud",
    "--project=alogic-playground",
    "functions",
    "deploy",
    "compile",
    "--region=europe-west1",
    "--service-account=playground@alogic-playground.iam.gserviceaccount.com",
    "--entry-point=com.argondesign.alogic.gcp.FunctionCompile",
    "--runtime=java11",
    "--memory=512MB",
    "--timeout=80s",
    "--trigger-http",
    "--allow-unauthenticated",
    s"--source=${assembly.value.getParent}"
  ) ! ProcessLogger { s: String => log.info(s) } ensuring { _ == 0 }
}
