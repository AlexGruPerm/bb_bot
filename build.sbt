import sbt.*
import Settings._

name := "bb_bot_project"

ThisBuild / organization := "yakushev"
ThisBuild / version      := "0.0.1"
ThisBuild / scalaVersion := "2.13.16"

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    Settings.commonSettings,
    libraryDependencies ++= commonDependencies
  )

lazy val db = (project in file("db"))
  .settings(
    name := "db",
    Settings.commonSettings,
    libraryDependencies ++= dbDependencies
  )
  .dependsOn(common)

lazy val bybit = (project in file("bybit"))
  .settings(
    name := "bybit",
    Settings.commonSettings,
    libraryDependencies ++= httpDependencies
  )
  .dependsOn(common)

lazy val trade_bot = (project in file("trade_bot"))
  .settings(
    assembly / assemblyJarName := "trade_bot.jar",
    mainClass / run := Some("app.TradeBot"),
    name := "trade_bot",
    Settings.commonSettings,
    libraryDependencies ++= bot4sDependencies,
    commonAssemblySettings
  )
  .dependsOn(db, bybit)

lazy val gather = (project in file("gather"))
  .settings(
    assembly / assemblyJarName := "gather.jar",
    mainClass / run := Some("app.Gather"),
    name := "gather",
    Settings.commonSettings,
    commonAssemblySettings
  )
  .dependsOn(db, bybit)

lazy val global = project
  .in(file("."))
  .enablePlugins(AssemblyPlugin)
  .enablePlugins(ScapegoatSbtPlugin)
  .settings(Settings.commonSettings, commonAssemblySettings)
  .aggregate(
    gather,
    bybit,
    trade_bot,
    db,
    common
  )

lazy val commonAssemblySettings = Seq(
  assembly / assemblyMergeStrategy := {
    // Netty versions.properties — берём первый
    case PathList("META-INF", "io.netty.versions.properties")     => MergeStrategy.first
    case PathList("META-INF/versions/11/", "module-info.class")   => MergeStrategy.first
    case "deriving.conf" => MergeStrategy.first

    case PathList("reference.conf")                  => MergeStrategy.concat
    case PathList("application.conf")                => MergeStrategy.concat
    case PathList("META-INF", "services", _ @ _*)    => MergeStrategy.filterDistinctLines

    case PathList("module-info.class")               => MergeStrategy.discard
    case PathList("META-INF", "versions", _ @ _*)    => MergeStrategy.discard

    case PathList("META-INF", "MANIFEST.MF")         => MergeStrategy.discard
    case PathList("META-INF", "INDEX.LIST")          => MergeStrategy.discard
    case PathList("META-INF", "DEPENDENCIES")        => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) if xs.nonEmpty && xs.last.toLowerCase.endsWith(".sf")  => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) if xs.nonEmpty && xs.last.toLowerCase.endsWith(".dsa") => MergeStrategy.discard

    case x =>
      val old = (assembly / assemblyMergeStrategy).value
      old(x)
  }
)