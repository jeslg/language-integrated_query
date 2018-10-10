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
          _ <- Monad[StateT[Future, String, ?]].whenM(res._1 != self)(
            StateT.liftM(ctx.run(quote {
              query[People]
                .filter(_.name == lift(self))
                .update(_.name -> lift(res._1))
            })))
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
          _ <- Monad[StateT[Future, String, ?]].whenM(res._1 != xs.head)(
            StateT.liftM(ctx.run(quote {
              query[People]
                .filter(_.name == lift(self))
                .update(_.age -> lift(res._1))
            })))
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

  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  //
  // =>
  //
  // SELECT x.name, x.age FROM People x
  waitResult(Primitives.getPeople(tr))

  // SELECT x7.name FROM people x7
  //
  // =>
  //
  // SELECT x1.name FROM People x1
  waitResult(Primitives.getPeopleName_(tr))

  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  //
  // =>
  //
  // SELECT x1.age FROM People x1
  waitResult(Primitives.getPeopleAge_(tr))

  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  //
  // =>
  //
  // SELECT p.name, p.age FROM People p WHERE 30 <= p.age AND p.age < 40
  waitResult(Primitives.getPeopleOnTheirThirties_(tr))

  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  //
  // =>
  //
  // SELECT w.name FROM People w WHERE 30 <= w.age AND w.age < 40
  waitResult(AbstractingOverValues.range(tr)(20, 40))

  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  //
  // =>
  //
  // SELECT w.name FROM People w WHERE 30 <= w.age AND w.age < 40
  waitResult(AbstractingOverAPredicate.range_(tr)(20, 40))
   
  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  //
  // =>
  //
  // SELECT w.name 
  //   FROM People u, People u1, People w 
  //   WHERE u.name = 'Edna' AND 
  //         u1.name = 'Bert' AND 
  //         u.age <= w.age AND 
  //         w.age < u1.age
  waitResult(ComposingQueries.compose(tr)("Edna", "Bert"))

  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Cora']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Drew']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // UPDATE people SET name = ? WHERE name = ? - binds: ['DREW', 'Drew']
  // UPDATE people SET name = ? WHERE name = ? - binds: ['CORA', 'Cora']
  // SELECT x7.name FROM people x7
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Alex']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['CORA']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Bert']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Edna']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['Fred']
  // SELECT x3.age FROM people x3 WHERE x3.name = ? - binds: ['DREW']
  waitResult(
    Primitives.uppercasePeopleOnTheirThirties(tr) >> Primitives.getPeople(tr))
   
  waitResult(
    Primitives.lowercasePeopleOnTheirThirties(tr) >> Primitives.getPeople(tr))
}

