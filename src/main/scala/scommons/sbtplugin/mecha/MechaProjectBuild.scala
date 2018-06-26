package scommons.sbtplugin.mecha

import org.stormenroute.mecha.{ConfigParsers, Repo}
import sbt._

/**
  * Optionally mixed into projects builds within a super-repository
  * for source-level dependencies.
  *
  * NOTE: Temporarily placed here, until this PR is merged:
  *       https://github.com/storm-enroute/mecha/pull/7
  */
trait MechaProjectBuild {

  /** Dirty hack because sbt does not expose BuildUnit.localBase easily.
    */
  def buildBase: File = {
    val currDir = file(".")
    if (currDir.getAbsoluteFile.getParentFile.getName == repoName) currDir
    else file(repoName)
  }

  def repoName: String

  /** Location of the repository configuration in the super-repo.
    */
  def repositoriesPath: String = "../repos.conf"

  final def repositoriesFile: File = new File(buildBase, repositoriesPath)

  /** Holds repository configuration if repo is checked out within a super-repo.
    */
  lazy val repositories: Option[collection.Map[String, Repo]] = {
    if (repositoriesFile.exists) {
      Some(ConfigParsers.reposFromHocon(repositoriesFile))
    }
    else None
  }

  /** List of super-repo projects dependencies.
    *
    * {{{
    * Seq(
    *   ("repo-1", "project-id-1", Some("test")),
    *   ("repo-1", "project-id-2", None),
    *   ("repo-2", "project-id-3", None),
    *   
    *   // CrossProject IDs may end up with JVM/JS suffixes
    *   //
    *   ("repo-2", "project-idJVM", None),
    *   ("repo-2", "project-idJS", None)
    * )
    * }}}
    */
  def superRepoProjectsDependencies: Seq[(String, String, Option[String])]

  /** List of project runtime dependencies.
    */
  def runtimeDependencies: Def.Initialize[Seq[ModuleID]]

  /** Maps project dependencies in the build to their projects within a super-repo.
    *
    * {{{
    * Seq(
    *   dependency1 -> ("repo-1", "project-id-1", Some("test")),
    *   dependency2 -> ("repo-1", "project-id-2", None),
    *   dependency3 -> ("repo-2", "project-id-3", None),
    *   
    *   // CrossProject IDs may end up with JVM/JS suffixes
    *   //
    *   dependency4 -> ("repo-2", "project-idJVM", None),
    *   dependency5 -> ("repo-2", "project-idJS", None)
    * )
    * }}}
    */
  def superRepoDependenciesMappings: Def.Initialize[Seq[(ModuleID, (String, String, Option[String]))]] = Def.setting {
    val deps = runtimeDependencies.value

    superRepoProjectsDependencies.flatMap { case projDep@(_, proj, _) =>
      val projStripped = proj
        .stripSuffix("JVM")
        .stripSuffix("JS")
      
      deps.find(_.name == proj)
        .orElse(deps.find(_.name == projStripped))
        .map { dep =>
          (dep, projDep)
        }
    }
  }

  /** Resolves the artifact dependencies based on the super-repo.
    *
    * @return dependencies that can be resolved through projects in super-repo or empty list
    */
  def excludeSuperRepoDependencies: Def.Initialize[Seq[ModuleID]] = Def.setting {
    repositories match {
      case None => Nil
      case Some(repos) => superRepoDependenciesMappings.value.collect {
        case (dep, (repo, _, _)) if repoDirExists(repos, repo) => dep
      }
    }
  }

  private def repoDirExists(repos: collection.Map[String, Repo], repo: String): Boolean = {
    new File(buildBase, getRepoDir(repos, repo)).exists
  }

  private def getRepoDir(repos: collection.Map[String, Repo], repo: String) = {
    repos.get(repo) match {
      case Some(r) => s"../${r.dir}"
      case None =>
        throw new IllegalArgumentException(s"Repo '$repo' not found, super-repositories: $repos")
    }
  }

  implicit class ProjectOps(p: Project) {

    /**
      * Returns the version of the project depending directly on projects in the super-repository,
      * if a super-repository is present.
      */
    def dependsOnSuperRepo: Project = repositories match {
      case None => p
      case Some(repos) =>
        superRepoProjectsDependencies.filter { case (repo, _, _) =>
          repoDirExists(repos, repo)
        }.foldLeft(p) { (proj, dep) =>
          val (repo, project, configuration) = dep
          val other = ProjectRef(
            uri(getRepoDir(repos, repo)),
            project
          )
          configuration match {
            case Some(c) => proj.dependsOn(other % c)
            case None => proj.dependsOn(other)
          }
        }
    }
  }
}
