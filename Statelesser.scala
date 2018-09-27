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

    def selNameAge[Per] = new InitialSAlg[Person, Per, (String, Int)] {
      def apply[Q[_]](alg: Person[Q, Per]): Q[(String, Int)] =
        alg.self.tuple2(alg.name.get, alg.age.get)
    }

    def getPeople0[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[((String, Int))]] = {
      import ctx.all, all.alg.{her, him}
      for {
        ws <- (all composeLens her)(selNameAge)
        ms <- (all composeLens him)(selNameAge)
      } yield ms ++ ws
    }

    getPeople0(ctx).run(cps)

    def selSelf[Per] = new InitialSAlg[Person, Per, Per] {
      def apply[Q[_]](alg: Person[Q, Per]): Q[Per] = alg.self.get
    }

    def getPeople[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[Per]] = {
      import ctx.all, all.alg.{her, him}
      for {
        ws <- (all composeLens her)(selSelf)
        ms <- (all composeLens him)(selSelf)
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

    def selList[Per] = new InitialSAlg[Person, Per, List[(String, Int)]] {
      def apply[Q[_]](alg: Person[Q, Per]): Q[List[(String, Int)]] = {
        implicit val M = alg.self
        for {
          age <- alg.age.get
          name <- alg.name.get
        } yield if (30 <= age && age < 40) (name, age).point[List] 
                else List.empty
      }
    }
    
    def getPeopleOnTheirThirties[Per, Cou, P[_]: Monad, Cous](
        ctx: Couples[Per, Cou, P, Cous]): P[List[(String, Int)]] = {
      import ctx.all, all.alg.{her, him}
      for {
        ws <- (all composeLens her).fold(selList) 
        ms <- (all composeLens him).fold(selList) 
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
  
  case class Person[P[_], Per](
    name: LensAlg[P, String],
    age: LensAlg[P, Int])(implicit
    val self: MonadState[P, Per])

  case class Couple[Per, P[_], Cou](
    her: LensAlgHom[Person, P, Per],
    him: LensAlgHom[Person, P, Per])(implicit
    val self: MonadState[P, Cou])

  // XXX: We need first order polymorphic functions in Scala! Could
  // kind-projector or shapeless help us with that?
  def selNameAge[Per] = new InitialSAlg[Person, Per, (String, Int)] {
    def apply[Q[_]](alg: Person[Q, Per]): Q[(String, Int)] =
      alg.self.tuple2(alg.name.get, alg.age.get)
  }

  // We delegate to a traversal of people (using inner applicative)
  def getPeople[P[_], Peo](
      tr: TraversalAlgHom[Person, P, Peo]): P[List[(String, Int)]] =
    tr(selNameAge)

  // or alternatively, we assume that the external program is applicative
  def getPeople_[P[_]: Applicative, Peo](
      tr: TraversalAlgHom[Person, P, Peo]): P[List[(String, Int)]] = {
    import tr.alg.{name, age}
    ((tr composeLens name).getAll |@| (tr composeLens age).getAll)(_ zip _)
  }

  // raw version
  def getPeopleName[P[_]: Functor, Peo](
      tr: TraversalAlgHom[Person, P, Peo]): P[List[String]] =
    getPeople(tr).map(_.map(_._1))

  // nicer version
  def getPeopleName[P[_], Peo](
      tr: TraversalAlgHom[Person, P, Peo]): P[List[String]] =
    (tr composeLens tr.alg.name).getAll

  // raw version
  def getPeopleOnTheirThirties[P[_]: Functor, Peo](
      tr: TraversalAlgHom[Person, P, Peo]): P[List[(String, Int)]] =
    getPeople(tr).map(_.filter { case (_, age) => 30 <= age && age < 40 })

  // nicer version

  def isOnThirties[Per] = new InitialSAlg[Person, Per, Boolean] {
    def apply[Q[_]](alg: Person[Q, Per]): Q[Boolean] = {
      implicit val _ = alg.self
      alg.age.get.map(v => 30 <= v && v < 40)
    }
  }

  def getPeopleOnTheirThirties_[P[_]: Functor, Peo](
      tr: TraversalAlgHom[Person, P, Peo]): P[List[(String, Int)]] =
    tr.filter(isOnThirties, selNameAge)(implicitly, tr.alg.self)

  def getHerAges[P[_], Cou, Per](
      tr: TraversalAlgHom[Couple[Per, ?[_], ?], P, Cou]): P[List[Int]] = {
    import tr.alg.her, her.alg.age
    (tr composeLens her composeLens age).getAll
  }

  def isHerOlder[Cou, Per] = 
    new InitialSAlg[Couple[Per, ?[_], ?], Cou, Boolean] {
      def apply[Q[_]](alg: Couple[Per, Q, Cou]): Q[Boolean] = {
        import alg.{her, him}
        implicit val _ = alg.self
        ((her composeLens her.alg.age).get |@| 
         (him composeLens him.alg.age).get)(_ > _ )
      }
    }

  def selNameDiff[Cou, Per] = 
    new InitialSAlg[Couple[Per, ?[_], ?], Cou, (String, Int)] {
      def apply[Q[_]](alg: Couple[Per, Q, Cou]): Q[(String, Int)] = {
        import alg.{her, him}
        implicit val _ = alg.self
        (her composeLens her.alg.name).get tuple
          (((her composeLens her.alg.age).get |@| 
            (him composeLens him.alg.age).get) { _ - _ })
      }
    }
  
  def difference[P[_]: Functor, Cou, Per](
      tr: TraversalAlgHom[Couple[Per, ?[_], ?], P, Cou]): P[List[(String, Int)]] = {
    tr.filter(isHerOlder, selNameDiff)(implicitly, tr.alg.self)
  }

  def isOnRange[Per](a: Int, b: Int) = new InitialSAlg[Person, Per, Boolean] {
    def apply[Q[_]](alg: Person[Q, Per]): Q[Boolean] = {
      implicit val _ = alg.self
      alg.age.get.map(v => a <= v && v < b)
    }
  }

  def range[P[_]: Functor, Per](
      tr: TraversalAlgHom[Person, P, Per])(
      a: Int, b: Int): P[List[(String, Int)]] =
    tr.filter(isOnRange(a, b), selNameAge)(implicitly, tr.alg.self)

  def satisfies[P[_]: Functor, Peo](
      tr: TraversalAlgHom[Person, P, Peo])(
      p: InitialSAlg[Person, Peo, Boolean]): P[List[(String, Int)]] =
    tr.filter(p, selNameAge)(implicitly, tr.alg.self)

  def range_[P[_]: Functor, Per](
      tr: TraversalAlgHom[Person, P, Per])(
      a: Int, b: Int): P[List[(String, Int)]] =
    satisfies(tr)(isOnRange(30, 40))

  def isSameName[Per](s: String) = new InitialSAlg[Person, Per, Boolean] {
    def apply[Q[_]](alg: Person[Q, Per]): Q[Boolean] = {
      implicit val _ = alg.self
      alg.name.get.map(_ == s)
    }
  }

  def selAge[Per] = new InitialSAlg[Person, Per, Int] {
    def apply[Q[_]](alg: Person[Q, Per]): Q[Int] = alg.age.get
  }

  def getAge[P[_]: Functor, Per](
      tr: TraversalAlgHom[Person, P, Per])(
      name: String): OptionT[P, Int] =
    OptionT(tr.filter(isSameName(name), selAge)(implicitly, tr.alg.self)
      .map(_.headOption))

  def compose[P[_]: Monad, Per](
      tr: TraversalAlgHom[Person, P, Per])(
      s: String,
      t: String): P[Option[List[(String, Int)]]] =
    (for {
      a1 <- getAge(tr)(s)
      a2 <- getAge(tr)(t)
      r  <- range(tr)(a1, a2).liftM[OptionT]
    } yield r).run
}

