import java.io.FileInputStream
import java.util.concurrent.TimeUnit._
import org.eclipse.jgit.lib.ProgressMonitor
import scala.sys.process._
import com.madgag.compress.CompressUtil._
import scala.util.matching.Regex
import scalax.file.Path
import scalax.file.ImplicitConversions._
import java.lang.System.nanoTime
import scalax.io.Codec

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
    "jgit"
    // "git"
  ).map(benchmarkSuite / "repos" / _)

  val bfgJars = Seq("1.4.0","1.5.0","1.6.0rc-SNAPSHOT").map(fix => Path.fromString(s"/home/roberto/bfg-demo/bfg-$fix.jar"))

  val commandRegex = "(.+?)(?:==(.*))?".r

  repoSpecDirs.foreach { repoSpecDir =>

    (repoSpecDir / "bfg-commands.txt").lines().foreach {
      def runBfgJobsFor(bfgParams: String) {
        bfgJars.foreach {
          bfgJar =>
            val repoDir = extractRepoFrom(repoSpecDir / "repo.git.zip")

            measureTask {
              println(s"bfgJar = ${bfgJar.name} params='$bfgParams' repo=${repoSpecDir.name}")
              s"java -jar ${bfgJar.path} $bfgParams ${repoDir.path}" !!
            }
        }
      }

      def runGfbJobFor(gfbParams: String) {
          val repoDir = extractRepoFrom(repoSpecDir / "repo.git.zip")

          measureTask {
            println(s"git-filter-branch params='${gfbParams.take(20)}' repo=${repoSpecDir.name}")
            s"git filter-branch --git-dir=${repoDir.path} $gfbParams ${repoDir.path}" !!
          }
      }

      commandLine =>

      commandLine match {
        case commandRegex(bfgParams, null) => runBfgJobsFor(bfgParams)
        case commandRegex(bfgParams, gfbParams) =>
          runBfgJobsFor(bfgParams)
          runGfbJobFor(gfbParams)
      }

    }
  }


  def measureTask[T](block: => T) = {
    val start = nanoTime
    val result = block
    val duration = nanoTime - start
    println("completed in %,d ms.".format(NANOSECONDS.toMillis(duration)))
    result
  }

  case class BFGExecution(bfgJar: Path, bfgParams: Path, repoDir: Path)
}
