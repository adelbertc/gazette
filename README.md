# Gazette
Toy project serving to illustrate that it's possible to write pure functional systems in Scala.

Gazette is a to-do list service.

## Usage
### Server
1. Check/change the values in `src/main/resources/server.cfg`.
2. `sbt run-main gazette.Server`

### Cli
1. `sbt run-main gazette.Cli --host <HOST HERE, e.g. 127.0.0.1> --port <PORT HERE, e.g. 8080>

`sbt run-main gazette.Cli --help` for help.

### Client
```scala
import gazette.{Client, Todo}

import org.http4s.Uri

import scalaz.concurrent.Task

val todo = Todo("lunch", "personal", Some(Date.valueOf("2015-06-13")), List("food"))

// type GazetteAction[A] = Kleisli[Task, Uri, A]
// Alternatively, Client.insert(todo) *> Client.todo w/ import scalaz.syntax.apply._
val action: GazetteAction[List[Todo]] =
  for {
    _ <- Client.insert(todo)  // insert
    r <- Client.todo          // fetch all to-dos
  } yield r

val uri: Uri = Client.uriWith(Uri.IPv4("127.0.0.1"), 8080)
val task: Task[List[Todo]] = action.run(uri)
val printTask: Task[Unit] = task.map(t => println(t.toString))

printTask.run // run the Task to actually print
```

## License
Code is provided under the BSD 2-Clause license available at http://opensource.org/licenses/BSD-2-Clause. Please
see the [LICENSE](LICENSE) file for more details.
