package gazette

import scalaz.ImmutableArray
import scalaz.concurrent.Task

// Coming soon to a Scalaz near you: https://github.com/scalaz/scalaz/pull/946
trait TaskApp {
  def run(args: ImmutableArray[String]): Task[Unit] = runl(args.toList)

  def runl(args: List[String]): Task[Unit] = runc

  def runc: Task[Unit] = Task.now(())

  final def main(args: Array[String]): Unit =
    run(ImmutableArray.fromArray(args)).run
}
