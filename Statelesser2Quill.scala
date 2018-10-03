package org.hablapps.liq

import scalaz._, Scalaz._

import org.hablapps.statelesser._

import Statelesser2._

import io.getquill._

import scala.concurrent._, ExecutionContext.Implicits.global
import scala.concurrent.duration._

import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

object Statelesser2Quill extends App {
    
  case class People(name: String, age: Int)
  case class Couples(her: String, him: String)

  // XXX: here we show the same "url" provided by Quill's tutorial. Update it to
  // match your MySql credentials.
  val config = ConfigFactory.empty()
    .withValue("url", ConfigValueFactory.fromAnyRef(
      "mysql://localhost:3306/database?user=root&password=root"))
    .withValue("database", ConfigValueFactory.fromAnyRef("quill"))
    .withValue("poolMaxQueueSize", ConfigValueFactory.fromAnyRef(1024))
    .withValue("poolMaxObjects", ConfigValueFactory.fromAnyRef(32))
    .withValue("poolMaxIdle", ConfigValueFactory.fromAnyRef(999999999))
    .withValue("poolValidationInterval", ConfigValueFactory.fromAnyRef(60))

  val ctx = new MysqlAsyncContext(SnakeCase, config)
  import ctx._

  val person = Person[StateT[Future, String, ?], String](
    LensAlg(
      λ[State[String, ?] ~> StateT[Future, String, ?]] { sx =>
        for {
          self <- StateT.get[Future, String]
          res <- sx(self).point[StateT[Future, String, ?]]
          _ <- StateT.liftM(ctx.run(quote {
            query[People]
              .filter(_.name == lift(self))
              .update(_.name -> lift(res._1))
          }))
          _ <- StateT.put[Future, String](res._1)
        } yield res._2
      }),
    LensAlg(
      λ[State[Int, ?] ~> StateT[Future, String, ?]] { sx =>
        for {
          self <- StateT.get[Future, String]
          xs <- StateT.liftM(ctx.run(quote {
            query[People]
              .filter(_.name == lift(self))
              .map(_.age)
          }))
          res <- sx(xs.head).point[StateT[Future, String, ?]]
          _ <- StateT.liftM(ctx.run(quote {
            query[People]
              .filter(_.name == lift(self))
              .update(_.age -> lift(res._1))
          }))
        } yield res._2
      }))

  val tr: TraversalAlgHom[Person, Future, String] = TraversalAlgHom(
    person, 
    λ[StateT[Future, String, ?] ~> ListT[Future, ?]] { sx =>
      for {
        k <- ListT(ctx.run(quote { query[People].map(_.name) }))
        out <- ListT(sx(k).map(res => List(res._2)))
      } yield out
    })

  def waitResult[A](p: Future[A]): Unit =
    println(Await.result(p, 5000 millis))

  waitResult(Primitives.getPeople(tr))
  waitResult(Primitives.getPeopleName(tr))
  waitResult(Primitives.getPeopleOnTheirThirties(tr))
  waitResult(AbstractingOverValues.range(tr)(20, 40))
  waitResult(AbstractingOverAPredicate.range_(tr)(20, 40))
  waitResult(ComposingQueries.compose(tr)("Edna", "Bert"))
  waitResult(
    Primitives.uppercasePeopleOnTheirThirties(tr) >> Primitives.getPeople(tr))
  waitResult(
    Primitives.lowercasePeopleOnTheirThirties(tr) >> Primitives.getPeople(tr))
}

