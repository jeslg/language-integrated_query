// package org.hablapps.liq

// import slick.driver.MySQLDriver.api._

// import scala.concurrent._, ExecutionContext.Implicits.global
// import scala.concurrent.duration._

// import org.hablapps.statelesser._

// import Statelesser2._

// import scalaz._, Scalaz._

// object Statelesser2Slick extends App {

//   val db = Database.forConfig("default")

//   class People(tag: Tag) extends Table[(String, Int)](tag, "people") {
//     def name = column[String]("name")
//     def age = column[Int]("age")
//     def * = (name, age)
//   }
//   val people = TableQuery[People]

//   try {

//     println("People")

//     val q = people.result

//     val res = db.run(q).map(_.foreach {
//       case (name, age) => println(s"($name, $age)")
//     })

//     Await.result(res, 5000 millis)

//     import Effect.{Read, Write}

//     def getPerson(name: Rep[String]) =
//       people.filter(_.name === name)

//     def getPersonName(name: Rep[String]) =
//       getPerson(name).map(_.name)

//     def gpnResult(name: Rep[String]) = getPersonName(name).result.head

//     def gpnUpdate(name: Rep[String], v: String) = getPersonName(name).update(v)

//     def evolve[A, Out](
//         res: Rep[String] => DBIOAction[A, NoStream, Read with Write],
//         upd: (Rep[String], A) => DBIOAction[Int, NoStream, Read with Write],
//         name: Rep[String],
//         sx: State[A, Out]): DBIOAction[Out, NoStream, Read with Write] =
//       for {
//         a <- res(name)
//         (a2, out) = sx(a)
//         _ <- upd(name, a2)
//       } yield out

//     def evolvePersonName[Out](
//         name: Rep[String],
//         sx: State[String, Out]): DBIOAction[Out, NoStream, Read with Write] =
//       evolve(gpnResult, gpnUpdate, name, sx)

//     implicit def msDBIOAction: Monad[DBIOAction[?, NoStream, Read with Write]] =
//       new Monad[DBIOAction[?, NoStream, Read with Write]] {
//         def point[A](a: => A) = DBIO.successful(a)
//         def bind[A, B](
//             fa: DBIOAction[A, NoStream, Read with Write])(
//             f: A => DBIOAction[B, NoStream, Read with Write]) =
//           fa flatMap f
//       }

//     def nat0 =
//       new (State[String, ?] ~>
//            StateT[DBIOAction[?, NoStream, Read with Write], String, ?]) {
//         def apply[A](sx: State[String, A]) =
//           for {
//             n <- StateT.get[DBIOAction[?, NoStream, Read with Write], String]
//             out <- StateT.liftM[
//               DBIOAction[?, NoStream, Read with Write],
//               String,
//               A](evolvePersonName(n, sx))
//           } yield out
//       }

//     // Evolving age

//     def getPersonAge(name: Rep[String]) =
//       getPerson(name).map(_.age)

//     def gpaResult(name: Rep[String]) = getPersonAge(name).result.head

//     def gpaUpdate(name: Rep[String], v: Int) = getPersonAge(name).update(v)

//     def evolvePersonAge[Out](
//         name: Rep[String],
//         sx: State[Int, Out]): DBIOAction[Out, NoStream, Read with Write] =
//       evolve(gpaResult, gpaUpdate _, name, sx)

//     // XXX: we need a polymorphic method as input (for all A) to modularize this
//     val nat1 = new (State[Int, ?] ~>
//                     StateT[DBIOAction[?, NoStream, Read with Write], String, ?]) {
//       def apply[A](sx: State[Int, A]) =
//         for {
//           n <- StateT.get[DBIOAction[?, NoStream, Read with Write], String]
//           out <- StateT.liftM[
//             DBIOAction[?, NoStream, Read with Write],
//             String,
//             A](evolvePersonAge(n, sx))
//         } yield out
//       }

//     val person =
//       Person[StateT[DBIOAction[?, NoStream, Read with Write], String, ?], String](
//         LensAlg(nat0),
//         LensAlg(nat1))(
//         StateT.stateTMonadState[String, DBIOAction[?, NoStream, Read with Write]])

//     val tr: TraversalAlgHom[Person, Future, String] = TraversalAlgHom(
//       person,
//       λ[StateT[DBIOAction[?, NoStream, Read with Write], String, ?] ~>
//         ListT[Future, ?]] { sx =>
//         for {
//           k <- ListT[Future, String](db.run(people.map(_.name).result.map(_.toList)))
//           out <- ListT(db.run(sx(k)).map(res => List(res._2)))
//         } yield out
//       })

//     def waitResult[A](p: Future[A]): Unit =
//       println(Await.result(p, 5000 millis))

//     waitResult(Primitives.getPeople(tr))

//     waitResult(Primitives.getPeopleName_(tr))

//     waitResult(Primitives.getPeopleAge_(tr))

//     waitResult(Primitives.getPeopleOnTheirThirties_(tr))

//     waitResult(AbstractingOverValues.range(tr)(20, 40))

//     waitResult(AbstractingOverAPredicate.range_(tr)(20, 40))

//     waitResult(ComposingQueries.compose(tr)("Edna", "Bert"))

//     waitResult(
//       Primitives.uppercasePeopleOnTheirThirties(tr) >> Primitives.getPeople(tr))

//     waitResult(
//       Primitives.lowercasePeopleOnTheirThirties(tr) >> Primitives.getPeople(tr))
//   } finally db.close
// }

