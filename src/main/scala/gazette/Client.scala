package gazette

import java.sql.Date

import org.http4s.Status.ResponseClass.Successful
import org.http4s.{Charset, EntityDecoder, EntityEncoder, Method, Query, Request, Response, Uri, UrlForm}
import org.http4s.Uri.{Authority, Host}
import org.http4s.argonaut.jsonOf
import org.http4s.client.blaze.defaultClient

import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.std.list._
import scalaz.syntax.traverse._

final case class ServerInfo(address: Host, port: Int)

final case class GazetteError(error: Response) extends Exception

object Client {
  type GazetteAction[A] = Kleisli[Task, Uri, A]

  def uriFromInfo(info: ServerInfo): Uri = uriWith(info.address, info.port)

  def uriWith(address: Host, port: Int): Uri =
    Uri(authority = Some(Authority(host = address, port = Some(port))), path = "/gazette/todo")

  private implicit val listTodoEntityDecoder: EntityDecoder[List[Todo]] = jsonOf[List[Todo]]

  private implicit val urlFormEntityEncoder: EntityEncoder[UrlForm] = UrlForm.entityEncoder(Charset.`UTF-8`)

  private def jsonAction[A : EntityDecoder](f: Uri => Uri): GazetteAction[A] =
    Kleisli { uri =>
      defaultClient(f(uri)).flatMap {
        case Successful(resp) => resp.as[A]
        case err              => Task.fail(GazetteError(err))
      }
    }

  private def jsonQuery[A : EntityDecoder](queryParam: (String, String)): GazetteAction[A] =
    jsonAction(Lenses.uriQuery.set(Query.fromPairs(queryParam)))

  def insert(todo: Todo): GazetteAction[Todo] =
    Kleisli { uri =>
      val form = Map(("event", List(todo.event)), ("category", List(todo.category)), ("tags", todo.tags)) ++
                 todo.due.toList.map(date => ("due", List(date.toString))).toMap

      val request = Request(method = Method.POST, uri = uri).withBody(UrlForm(form))

      defaultClient(request).flatMap {
        case Successful(resp) => resp.as[Todo]
        case err              => Task.fail(GazetteError(err))
      }
    }

  def insertList(todos: List[Todo]): GazetteAction[List[Todo]] = todos.traverse(insert)

  def insertMany(todos: Todo*): GazetteAction[List[Todo]] = insertList(todos.toList)

  def todo: GazetteAction[List[Todo]] = jsonAction(identity)

  def category(cat: String): GazetteAction[List[Todo]] = jsonQuery(("category", cat))

  def today: GazetteAction[List[Todo]] = jsonAction(Lenses.uriPath.modify(_ ++ "/today"))

  def due(date: Date): GazetteAction[List[Todo]] = jsonQuery(("due", date.toString))

  def tag(tag: String): GazetteAction[List[Todo]] = jsonQuery(("tag", tag))

  def stats: GazetteAction[Stats] = jsonAction(Lenses.uriPath.modify(_ ++ "/stats"))
}
