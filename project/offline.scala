import java.io.FileInputStream

import sbt._
import Keys._

import SbtSupport.sbtLaunchJar
import LocalTemplateRepo.localTemplateCacheCreated
import Packaging.localRepoCreated

object offline {
  
  val runOfflineTests = TaskKey[Unit]("offline-tests", "runs tests to ensure templates can work with the offline repository.")
  
  // set up offline repo tests as integration tests.
  def settings: Seq[Setting[_]] = Seq(
    runOfflineTests <<= (localTemplateCacheCreated in TheActivatorBuild.localTemplateRepo,
                         localRepoCreated in TheActivatorBuild.dist,
                         sbtLaunchJar, 
                         streams) map offlineTestsTask,
    integration.tests <<= runOfflineTests
  )
  
  def offlineTestsTask(templateRepo: File, localIvyRepo: File, launcher: File, streams: TaskStreams): Unit = {
    // TODO - Use a target directory instead of temporary.
    IO.withTemporaryDirectory { dir =>
      IO.copyDirectory(templateRepo, dir)
      runofflinetests(dir, localIvyRepo, launcher, streams.log)
    }
  }
  
  def runofflinetests(templateRepo: File, localIvyRepo: File, launcher: File, log: sbt.Logger): Unit = {
    val results = 
      for {
        projectInfo <- findTestDirs(templateRepo)
        name = "[" + projectInfo._2 + ", " + projectInfo._1.name + "]"
        _ = log.info("Running test for template: " + name)
        result = runTest(localIvyRepo, projectInfo._1, launcher, log)
      } yield name -> result
    // TODO - Recap failures!
    if(results exists (_._2 != true)) {
      val failureCount = results.filterNot(_._2).length
      log.info("[OFFLINETEST] " + failureCount + " failures in " + results.length + " tests...")
      for((name, result) <- results) {
        log.info(" [OFFLINETEST] " + name + " - " + (if (result) "SUCCESS" else "FAILURE"))
      }
      sys.error("Tests were unsuccessful")
    } else {
      log.info("[OFFLINETEST] " + results.length + " tests successful.")
    }
    ()
  }

  def findTestDirs(root: File): Seq[(File, String)] = {
    // extract the template name from the activator.properties file
    def extractTemplateName(file: File): String = {
      val fis = new FileInputStream(file.getAbsolutePath)
      try {
        val properties = new java.util.Properties
        properties.load(fis)
        properties.getProperty("name")
      } finally {
        fis.close()
      }
    }

    for {
      dir <- (root.***).get
      if (dir / "project/build.properties").exists
      if (dir / "activator.properties").exists
      projectName = extractTemplateName((dir / "activator.properties").getAbsoluteFile)
    } yield (dir, projectName)
  }
  
  def runTest(localIvyRepo: File, template: File, launcher: File, log: sbt.Logger): Boolean = {
    sbt.IO.withTemporaryFile("sbt", "repositories") { repoFile =>
      makeRepoFile(repoFile, localIvyRepo)
      def sbt(args: String*) = runSbt(launcher, repoFile, template, log)(args)
      sbt("update")
    }
  }
  
  def runSbt(launcher: File, repoFile: File, cwd: File, log: sbt.Logger)(args: Seq[String]): Boolean = 
    IO.withTemporaryDirectory { globalBase =>
      val jvmargs = Seq(
        "-Dsbt.repository.config="+repoFile.getCanonicalPath,
        "-Dsbt.override.build.repos=true",
        // TODO - Enough for fresh cache?
        "-Dsbt.ivy.home="+(globalBase / ".ivy2").getAbsolutePath,
        // TODO - we should consolidate on the two supported sbt versoins if we can.
        "-Dsbt.global.base="+globalBase.getAbsolutePath
      )
      val cmd = Seq("java") ++ jvmargs ++ Seq("-jar", launcher.getCanonicalPath) ++ args
      Process(cmd, cwd) ! log match {
        case 0 => true
        case n => false
      }
    }
  
  def makeRepoFile(props: File, localIvyRepo: File): Unit = {
    // TODO - Don't hardcode the props file!
    IO.write(props,
"""
[repositories]
  activator-local: %s, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
""" format(localIvyRepo.getCanonicalFile.toURI.toString))
  }
}
