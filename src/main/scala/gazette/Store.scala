package gazette

import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.stream.{Process, io, text}

import scodec.stream.encode

object Store {
  private def separate(todo: Todo, sep: Char, tagSep: Char): String =
    List(todo.event, todo.category, todo.due.getOrElse(""), todo.tags.mkString(tagSep.toString)).mkString(sep.toString)

  private def liftProcess[A](as: List[A]): Process[Task, A] =
    Process.emitAll(as).evalMap(Task.now)

  def separatedBy(todos: List[Todo], sep: Char, tagSep: Char): Kleisli[Task, String, Unit] =
    Kleisli { path =>
      liftProcess(todos).
      map(todo => separate(todo, sep, tagSep)).
      intersperse("\n").
      pipe(text.utf8Encode).
      to(io.fileChunkW(path)).
      run
    }

  def binary(todos: List[Todo]): Kleisli[Task, String, Unit] =
    Kleisli { path =>
      encode.many(Todo.todoCodec).encode(liftProcess(todos)).
      map(_.toByteVector).
      to(io.fileChunkW(path)).
      run
    }
}
