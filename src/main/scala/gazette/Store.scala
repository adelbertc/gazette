package gazette

import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.stream.{Process, io, text}

object Store {
  private def separate(todo: Todo, sep: Char, tagSep: Char): String =
    List(todo.event, todo.category, todo.due.getOrElse(""), todo.tags.mkString(tagSep.toString)).mkString(sep.toString)

  def separatedBy(todos: List[Todo], sep: Char, tagSep: Char): Kleisli[Task, String, Unit] =
    Kleisli { path =>
      Process.emitAll(todos).
      evalMap(Task.now).
      map(todo => separate(todo, sep, tagSep)).
      intersperse("\n").
      pipe(text.utf8Encode).
      to(io.fileChunkW(path)).
      run
    }
}
