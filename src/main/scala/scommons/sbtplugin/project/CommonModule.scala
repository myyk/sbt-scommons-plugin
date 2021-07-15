package scommons.sbtplugin.project

import sbt.Keys._
import sbt._
import scommons.sbtplugin.mecha.MechaProjectBuild
import scoverage.ScoverageKeys._

trait CommonModule extends ProjectDef with MechaProjectBuild {

  override def definition: Project = Project(id = id, base = base)
    .dependsOn(internalDependencies: _*)
    .settings(CommonModule.settings: _*)
    .settings(
      libraryDependencies ++= (runtimeDependencies.value ++ testDependencies.value)
    )
    .settings(Seq(
      libraryDependencies --= excludeSuperRepoDependencies.value
    ))
    .dependsOnSuperRepo

  override def superRepoProjectsDependencies: Seq[(String, String, Option[String])] = Nil
}

object CommonModule {

  // got from here:
  // https://github.com/JetBrains/sbt-ide-settings
  //
  val ideExcludedDirectories = SettingKey[Seq[File]]("ide-excluded-directories")

  lazy val scala212 = "2.12.8"
  lazy val scala213 = "2.13.6"
  lazy val supportedScalaVersions = List(scala212, scala213)

  val settings: Seq[Setting[_]] = Seq(
    scalaVersion := scala212,
    crossScalaVersions := supportedScalaVersions,
    scalacOptions ++= Seq(
      //see https://docs.scala-lang.org/overviews/compiler-options/index.html#Warning_Settings
      //"-Xcheckinit",
      "-Xfatal-warnings",
      "-Xlint:_",
      "-Ywarn-macros:after", // Only inspect expanded trees when generating unused symbol warnings
      "-explaintypes",
      "-unchecked",
      "-deprecation",
      "-feature"
    ),
    //ivyScala := ivyScala.value map {
    //  _.copy(overrideScalaVersion = true)
    //},
    ideExcludedDirectories := {
      val base = baseDirectory.value
      List(
        base / ".idea",
        base / "target"
      )
    },
    //when run tests with coverage: "sbt clean coverage test it:test coverageReport && sbt coverageAggregate"
    coverageMinimum := 80,

    resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
  )
}
