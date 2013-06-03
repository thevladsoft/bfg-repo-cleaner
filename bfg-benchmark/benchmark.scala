import java.util.concurrent.TimeUnit._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.sys.process._
import com.madgag.compress.CompressUtil._
import scalax.file.defaultfs.DefaultPath
import scalax.file.Path
import scalax.file.ImplicitConversions._
import java.lang.System.nanoTime
import scalax.file.PathMatcher.IsDirectory
import scalax.io.{Input, Codec}

object Main extends App {

  implicit val codec = Codec.UTF8

  val scratchDir = Path.fromString("/dev/shm/repo.git")

  val benchmarkSuite = Path.fromString("/home/roberto/development/blob-fixing-git-cleaner-debugging/benchmark-suite/")


  def extractRepoFrom(zipPath: Path) = {
    val repoDir = scratchDir / "repo.git"

    repoDir.deleteRecursively(force = true)

    repoDir.createDirectory()

    zipPath.inputStream.acquireFor { stream => unzip(stream, repoDir) }

    repoDir
  }

  val repoSpecDirs = Seq(
    // "rails",
    "github-gem",
    "wine"
    // "jgit",
    //"gcc"
    // "git"
  ).map(benchmarkSuite / "repos" / _)

  val bfgJars = Seq("1.4.0","1.5.0","1.6.0").map(fix => Path.fromString(s"/home/roberto/bfg-demo/bfg-$fix.jar"))

  val commandRegex = "(.+?)(?:==(.*))?".r

  repoSpecDirs.foreach { repoSpecDir =>
    val repoName = repoSpecDir.name

    println(s"Repo : $repoName")

    (repoSpecDir / "commands").children().filter(IsDirectory).foreach { commandDir =>

      val commandName = commandDir.name

      def runJobFor(typ: String, processGen: ProcessGen):Option[Duration] = {
        val paramsPath = commandDir / s"$typ.txt"
        val repoDir = extractRepoFrom(repoSpecDir / "repo.git.zip")
        if (paramsPath.exists) {
          val process = processGen.genProcess(paramsPath, repoDir)
          Some(measureTask(s"$commandName - ${processGen.description}") {
            process!(ProcessLogger(_ => Unit))
          })
        } else None
      }

      val bfgExecutions: Seq[(String, Duration)] = bfgJars.map { bfgJar =>
        val desc = bfgJar.simpleName
        val duration = runJobFor("bfg", new ProcessGen {
          def genProcess(paramsInput: Input, repoPath: DefaultPath) =
            Process(s"java -jar ${bfgJar.path} ${paramsInput.string}", repoPath)

          val description = desc
        })
        duration.map(d => desc -> d)
      }.flatten

      val gfbDuration: Option[Duration] = runJobFor("gfb", new ProcessGen {
        lazy val description = "git filter-branch"
        def genProcess(paramsInput: Input, repoPath: DefaultPath) =
          Process(Seq("git", "filter-branch") ++ paramsInput.lines(), repoPath)
      })

      val samples = TaskExecutionSamples(bfgExecutions, gfbDuration)
      println(s"$repoName $commandName :: ${samples.summary}")
    }
  }

  case class TaskExecutionSamples(bfgExecutions: Seq[(String, Duration)], gfbExecution: Option[Duration]) {

    lazy val summary = {
      bfgExecutions.map { case (name,dur) => f"$name: ${dur.toMillis}%,d ms"}.mkString(", ") + gfbExecution.map {
        gfb => "  "+bfgExecutions.map { case (name,dur) => f"$name: ${gfb/dur}%2.1fx"}.mkString(", ")
      }.getOrElse("")
    }
  }

  def measureTask[T](description: String)(block: => T): Duration = {
    val start = nanoTime
    val result = block
    val duration = FiniteDuration(nanoTime - start, NANOSECONDS)
    println(s"$description completed in %,d ms.".format(duration.toMillis))
    duration
  }

  case class BFGExecution(bfgJar: Path, bfgParams: Path, repoDir: Path)

  trait ProcessGen {
    val description: String

    def genProcess(paramsInput: Input, repoPath: DefaultPath): ProcessBuilder
  }
}
