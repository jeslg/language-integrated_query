package org.hablapps
package liq

import scalaz._, Scalaz._
import statelesser._, GetEvidence._

object Statelesser {

  case class Person[P[_], Per](
    name: LensAlg[P, String], 
    age: LensAlg[P, Int])(implicit
    val self: MonadState[P, Per])

  case class Couple[Per, P[_], Cou](
    her: LensAlgHom[Person, P, Per], 
    him: LensAlgHom[Person, P, Per])(implicit
    val self: MonadState[P, Cou])

  case class Couples[Per, Cou, P[_], Cous](
    all: TraversalAlgHom[Couple[Per, ?[_], ?], P, Cou])(implicit
    val self: MonadState[P, Cous])

  case class SPerson(name: String, age: Int)
  case class SCouple(her: SPerson, him: SPerson)
  case class SCouples(all: List[SCouple])

  val ctx = make[Couples[SPerson, SCouple, State[SCouples, ?], SCouples]]

  val cps = SCouples(List(
    SCouple(SPerson("Alex", 60), SPerson("Bert", 55)),
    SCouple(SPerson("Cora", 33), SPerson("Drew", 31)),
    SCouple(SPerson("Edna", 21), SPerson("Fred", 60))))

  object Primitives {

    // XXX: does this make sense as statelesser apply parameter?
    trait Selector[Alg[_[_], _], X] {
      def apply[Q[_], S](alg: Alg[Q, S]): Q[X]
    }
      
    val selNameAge = new Selector[Person, (String, Int)] {
      def apply[Q[_], A](p: Person[Q, A]): Q[(String, Int)] =
        p.self.tuple2(p.name.get, p.age.get) 
    }

    def getPeople0[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[((String, Int))]] = {
      import ctx.all, all.alg.{her, him}
      for {
        ws <- (all composeLens her)(selNameAge.apply)
        ms <- (all composeLens him)(selNameAge.apply)
      } yield ms ++ ws
    }

    getPeople0(ctx).run(cps)

    def getPeople[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[Per]] = {
      import ctx.all, all.alg.{her, him}
      for {
        ws <- (all composeLens her)(_.self.get)
        ms <- (all composeLens him)(_.self.get)
      } yield ms ++ ws
    }

    getPeople(ctx).run(cps)

    def getPeopleName[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[String]] = {
      import ctx.all, all.alg.{her, him} 
      import her.alg.{name => herName}, him.alg.{name => hisName}
      for {
        ws <- (all composeLens her composeLens herName).getAll
        ms <- (all composeLens him composeLens hisName).getAll
      } yield ms ++ ws
    }

    getPeopleName(ctx).run(cps)

    def getPeopleOnTheirThirties0[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[(String, Int)]] = {
      for {
        ps <- getPeople0(ctx)
      } yield ps.filter { case (_, age) => 30 <= age && age < 40 }
    }

    getPeopleOnTheirThirties0(ctx).run(cps)

    val selList = new Selector[Person, List[(String, Int)]] {
      def apply[Q[_], S](p: Person[Q, S]): Q[List[(String, Int)]] = {
        implicit val M = p.self
        for {
          age  <- p.age.get
          name <- p.name.get
        } yield if (30 <= age && age < 40) (name, age).point[List] 
                else List.empty
      }
    }
    
    def getPeopleOnTheirThirties[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[(String, Int)]] = {
      import ctx.all, all.alg.{her, him}
      for {
        ws <- (all composeLens her).fold(selList.apply) 
        ms <- (all composeLens him).fold(selList.apply) 
      } yield ms ++ ws
    }

    getPeopleOnTheirThirties(ctx).run(cps)

    def getHers[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[String]] = {
      import ctx.all, all.alg.her, her.alg._
      (all composeLens her composeLens name).getAll 
    }
  }

  object QueryViaQuotation

  object AbstractingOverValues {
    
    def range[Per, Cou, P[_], Cous](
        cps: Couples[Per, Cou, P, Cous])(
        a: Int,
        b: Int): P[List[(String, Int)]] = {
      ???
    }

    range(ctx)(30, 40)(cps)
  }
}

object Statelesser2 {

}

