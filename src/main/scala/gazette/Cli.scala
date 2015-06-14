package gazette

import java.sql.Date

import gazette.Client.GazetteAction
import gazette.Util._

import net.bmjames.opts._

import org.http4s.Uri

import scalaz.{Applicative, Bind}
import scalaz.concurrent.Task
import scalaz.std.string.parseInt
import scalaz.syntax.apply._

object Cli extends TaskApp {
  val serverInfoParser: Parser[Uri] = {
    val host = strOption(short('n'), long("host"), metavar("HOST"), help("Host of server - e.g. 127.0.0.1")).map(Uri.IPv4.apply)
    val port = intOption(short('p'), long("port"), metavar("PORT"), help("Port of server - e.g. 8080"))
    (host |@| port)(Client.uriWith)
  }

  val maxWidth = 15

  def menu(date: Date): String =
    s"""
    Gazette CLI - ${date}
    --------------------------
    1. Create new
    2. Fetch all
    3. Fetch category
    4. Due today
    5. Due on ..
    6. Fetch tag
    0. Exit
    """

  val todaysMenu: Task[String] = currentDate.map(menu)

  val createTodo: GazetteAction[Option[Todo]] =
    (gprompt("Event name") |@| gprompt("Category") |@| gprompt("Due date(yyyy-MM-dd) (optional)") |@| gprompt("Tags, comma separated")) {
      case (e, c, d, t) =>
        val ts  =  t.split(",").toList.map(_.trim).filter(_.nonEmpty)
        if (d.isEmpty) Some(Todo(e, c, None, ts))
        else parseDate(d).map(date => Todo(e, c, Some(date), ts))
    }

  def mangle(s: String): String =
    if (s.size > maxWidth) s.take(maxWidth - 2) + ".." else s ++ (" " * (maxWidth - s.size))

  def prettyTodo(todo: Todo): String =
    s"Event: ${todo.event}\n" ++
    s"Category: ${todo.category}\n" ++
    todo.due.fold("")(d => s"Due: ${todo.due}\n") ++
    s"Tags: ${todo.tags.mkString(", ")}"

  def prettyList(tds: List[Todo]): String = {
    val bar = " | "
    val header = List("Event", "Category", "Due", "Tags").map(s => s ++ (" " * (maxWidth - s.size))).mkString(bar)
    val sep = "-" * header.size

    header ++ "\n" ++
    sep ++ "\n" ++
    tds.map { todo =>
      val dueDate = todo.due.fold(" " * maxWidth)(date => mangle(date.toString))
      val tagsString = todo.tags.mkString(",")
      List(mangle(todo.event), mangle(todo.category), dueDate, mangle(tagsString)).mkString(bar)
    }.mkString("\n")
  }

  def runAndPrint(action: GazetteAction[List[Todo]]): GazetteAction[Unit] =
    for {
      a <- action
      _ <- gputStrLn(prettyList(a))
    } yield ()

  def handleInput(n: Int): GazetteAction[Unit] =
    n match {
      case 1 =>
        for {
          ot <- createTodo
          _  <- ot.fold(gputStrLn("Unable to create Todo."))(td => Client.insert(td) *> gputStrLn(prettyTodo(td)))
        } yield ()
      case 2 => runAndPrint(Client.todo)
      case 3 =>
        for {
          c <- gprompt("Category")
          _ <- runAndPrint(Client.category(c))
        } yield ()
      case 4 => runAndPrint(Client.today)
      case 5 =>
        for {
          d <- gprompt("Date (yyyy-MM-dd)")
          _ <- parseDate(d).fold(gputStrLn("Invalid date."))(date => runAndPrint(Client.due(date)))
        } yield ()
      case 6 =>
        for {
          t <- gprompt("Tag")
          _ <- runAndPrint(Client.tag(t))
        } yield ()
      case 0 => gputStrLn("Goodbye..") *> Applicative[GazetteAction].point(sys.exit(0))
      case _ => gputStrLn("Bad input, try again.")
    }

  override def runl(args: List[String]): Task[Unit] = {
    val opts = info(serverInfoParser <*> helper,
                    progDesc("Connect to Gazette server w/ a CLI."),
                    header("Gazette CLI"))

    val loop =
      for {
        u   <- Task.delay(execParser(args.toArray, "gazette.Cli", opts))
        s   <- todaysMenu
        _   <- putStrLn(s)
        i   <- prompt("Enter an option")
        _   <- putStrLn("")
        _   <- parseInt(i).toOption.fold(putStrLn("Please enter a number."))(i => handleInput(i).run(u))
        _   <- putStrLn("")
      } yield ()

    Bind[Task].forever(loop)
  }
}
