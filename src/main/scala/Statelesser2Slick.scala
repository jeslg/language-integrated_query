package org.hablapps.liq

import slick.driver.MySQLDriver.api._

import scala.concurrent._, ExecutionContext.Implicits.global
import scala.concurrent.duration._

import org.hablapps.statelesser._

import Statelesser2._

import scalaz._, Scalaz._

object Statelesser2Slick extends App {

  val db = Database.forConfig("default")

  class People(tag: Tag) extends Table[(String, Int)](tag, "people") {
    def name = column[String]("name")
    def age = column[Int]("age")
    def * = (name, age)
  }
  val people = TableQuery[People]

  try {

    println("People")

    val q = people.result

    val res = db.run(q).map(_.foreach {
      case (name, age) => println(s"($name, $age)")
    })

    Await.result(res, 5000 millis)

    import Effect.{Read, Write}

    def q0(n: Rep[String]) = for {
      p <- people if (p.name === n)
    } yield p.name

    def a0(n: Rep[String]) = q0(n).result.head

    def a1(n: Rep[String], v: String) = q0(n).update(v)

    def a2[Out](
        n: Rep[String], 
        sx: State[String, Out]): DBIOAction[Out, NoStream, Read with Write] =
      for {
        s <- a0(n)
        (s2, out) = sx(s)
        _ <- a1(n, s2)
      } yield out

    implicit def msDBIOAction: Monad[DBIOAction[?, NoStream, Read with Write]] =
      new Monad[DBIOAction[?, NoStream, Read with Write]] {
        def point[A](a: => A) = DBIO.successful(a)
        def bind[A, B](
            fa: DBIOAction[A, NoStream, Read with Write])(
            f: A => DBIOAction[B, NoStream, Read with Write]) =
          fa flatMap f
      }

    val nat0 = new (State[String, ?] ~> 
                    StateT[DBIOAction[?, NoStream, Read with Write], String, ?]) {
        def apply[A](sx: State[String, A]) =
          for {
            n <- StateT.get[DBIOAction[?, NoStream, Read with Write], String]
            out <- StateT.liftM[
              DBIOAction[?, NoStream, Read with Write], 
              String, 
              A](a2(n, sx))
          } yield out
      }

    // XXX: this is boilerplate!

    def q0_(n: Rep[String]) = for {
      p <- people if (p.name === n)
    } yield p.age

    def a0_(n: Rep[String]) = q0_(n).result.head

    def a1_(n: Rep[String], v: Int) = q0_(n).update(v)

    def a2_[Out](
        n: Rep[String], 
        sx: State[Int, Out]): DBIOAction[Out, NoStream, Read with Write] =
      for {
        s <- a0_(n)
        (s2, out) = sx(s)
        _ <- a1_(n, s2)
      } yield out

    val nat1 = new (State[Int, ?] ~>
                    StateT[DBIOAction[?, NoStream, Read with Write], String, ?]) {
      def apply[A](sx: State[Int, A]) =
        for {
          n <- StateT.get[DBIOAction[?, NoStream, Read with Write], String]
          out <- StateT.liftM[
            DBIOAction[?, NoStream, Read with Write], 
            String, 
            A](a2_(n, sx))
        } yield out
      }

    val person = 
      Person[StateT[DBIOAction[?, NoStream, Read with Write], String, ?], String](
        LensAlg(nat0),
        LensAlg(nat1))(
        StateT.stateTMonadState[String, DBIOAction[?, NoStream, Read with Write]])

    val tr: TraversalAlgHom[Person, Future, String] = TraversalAlgHom(
      person,
      λ[StateT[DBIOAction[?, NoStream, Read with Write], String, ?] ~> 
        ListT[Future, ?]] { sx =>
        for {
          k <- ListT[Future, String](db.run(people.map(_.name).result.map(_.toList)))
          out <- ListT(db.run(sx(k)).map(res => List(res._2)))
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

  } finally db.close
}

