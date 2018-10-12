package org.hablapps.liq

import scalaz.{Lens => _, _}, Scalaz._

import monocle._, macros.Lenses, function.all._

object Optics {
  
  @Lenses case class Person(name: String, age: Int)
  @Lenses case class Couple(her: Person, him: Person)

  type People  = List[Person]
  type Couples = List[Couple]

  val couples = List(
    Couple(Person("Alex", 60), Person("Bert", 55)),
    Couple(Person("Cora", 33), Person("Drew", 31)),
    Couple(Person("Edna", 21), Person("Fred", 60)))

  import Person.{name, age}, Couple.{her, him}

  val both: Traversal[Couple, Person] = Traversal.applyN(her, him) 

  val all: Traversal[List[Couple], Couple] = each[List[Couple], Couple]

  def fst[A, B]: Lens[(A, B), A] = first

  def snd[A, B]: Lens[(A, B), B] = second

  def flat[A, B, C] = Iso[(A, (B, C)), (A, B, C)] 
    { case (a, (b, c)) => (a, b, c) } 
    { case (a, b, c) => (a, (b, c)) }

  implicit class TraversalOps[S, A](tr: Traversal[S, A]) {

    def contains(a: A): S => Boolean =
      tr.exist(_ == a)

    def filter(p: A => Boolean): S => List[A] =
      tr.foldMap[List[A]](a => if (p(a)) List(a) else List.empty)
  }

  implicit class LensOps[S, A](ln: Lens[S, A]) {
    // XXX: unlawful when `A` and `B` are pointing to the same value
    def *[B](other: Lens[S, B]): Lens[S, (A, B)] = 
      Lens[S, (A, B)](
        s => (ln.get(s), other.get(s)))(
        ab => s => other.set(ab._2)(ln.set(ab._1)(s)))
  }

  implicit class FoldOps[S, A](fl: Fold[S, A]) {

    def withFilter(p: A => Boolean): Fold[S, A] =
      new Fold[S, A] {
        def foldMap[M: Monoid](f: A => M)(s: S): M =
          fl.foldMap(a => if (p(a)) f(a) else Monoid[M].zero)(s)
      }

    def withMap[B](f: A => B): Fold[S, B] =
      new Fold[S, B] {
        def foldMap[M: Monoid](g: B => M)(s: S): M =
          fl.foldMap(g compose f)(s)
      }

    def *[B](other: Fold[S, B]): Fold[S, (A, B)] = 
      new Fold[S, (A, B)] {
        def foldMap[M: Monoid](f: ((A, B)) => M)(s: S): M =
          fl.getAll(s).zip(other.getAll(s)).foldMap(f)
      }

    def flatMap[B](f: A => Fold[S, B]): Fold[S, B] =
      new Fold[S, B] {
        def foldMap[M: Monoid](g: B => M)(s: S): M =
          fl.getAll(s).foldMap(f(_).foldMap(g)(s))
      }
  }

  implicit def foldMonad[S] = new Monad[Fold[S, ?]] {

    def point[A](a: => A): Fold[S, A] = new Fold[S, A] {
      def foldMap[M: Monoid](f: A => M)(s: S): M = f(a)
    }

    def bind[A, B](fa: Fold[S, A])(f: A => Fold[S, B]): Fold[S, B] =
      fa.flatMap(f)
  }

  object Primitives {

    val coupledPeople: Traversal[Couples, Person] = 
      all ^|->> both

    val people: Traversal[People, Person] = 
      each[People, Person]
    
    val getPeople: People => People = 
      people.getAll

    val getPeopleName: People => List[String] =
      (people ^|-> name).getAll

    val getPeopleOnTheirThirtiesFl: Fold[People, Person] =
      people.asFold.withFilter(p => 30 <= p.age && p.age < 40)

    val getPeopleOnTheirThirties: People => List[Person] =
      getPeopleOnTheirThirtiesFl.getAll

    val getHerAges: Couples => List[Int] =
      (all ^|-> her ^|-> age).getAll
  }

  object QueryViaQuotation {
    
    val differenceFl: Fold[Couples, (String, Int)] =
      (all ^|-> (him ^|-> age) * (her ^|-> name * age) ^<-> flat).asFold
        .withFilter { case (ma, _, wa) => wa > ma } 
        .withMap { case (ma, wn, wa) => (wn, wa - ma) }

    val difference: Couples => List[(String, Int)] = differenceFl.getAll

    difference(couples)
  }

  object AbstractingOverValues {
    import Primitives.coupledPeople

    val nameAgeFl = (coupledPeople ^|-> name * age).asFold

    val rangeFl: (Int, Int) => Fold[Couples, String] = { (a, b) =>
      nameAgeFl.withFilter { case (_, i) => a <= i && i < b }
               .composeLens(fst)
    }

    val range: (Int, Int) => Couples => List[String] = { (a, b) => 
      rangeFl(a, b).getAll 
    }

    range(30, 40)(couples)
  }

  object AbstractingOverAPredicate {
    import AbstractingOverValues.nameAgeFl

    val satisfiesFl: (Int => Boolean) => Fold[Couples, String] = { p =>
      nameAgeFl.withFilter { case (_, a) => p(a) }
               .composeLens(fst)
    }

    val satisfies: (Int => Boolean) => Couples => List[String] = { p =>
      satisfiesFl(p).getAll
    }

    satisfies(i => 30 <= i && i < 40)(couples)
  }

  object ComposingQueries {
    import AbstractingOverValues.rangeFl
    
    def getAgeFl(s: String): Fold[Couples, Int] =
      all.composeTraversal(both)
         .composeLens(name * age) 
         .asFold
         .withFilter { case (n, _) => n == s }
         .composeLens(snd)

    val composeFl = { (s: String, t: String) =>
      (getAgeFl(s) * getAgeFl(t)).flatMap { case (a, b) => rangeFl(a, b) }
    }

    // or alternatively
    val composeFl2 = { (s: String, t: String) =>
      for {
        a <- getAgeFl(s)
        b <- getAgeFl(t)
        x <- rangeFl(a, b)
      } yield x
    }

    val compose: (String, String) => Couples => List[String] = { (s, t) =>
      composeFl(s, t).getAll
    }

    compose("Alex", "Drew")(couples)
  }

  object Nested {

    @Lenses case class NestedOrg(departments: List[NestedDepartment])

    @Lenses case class NestedDepartment(dpt: String, employees: List[NestedEmployee])

    @Lenses case class NestedEmployee(emp: String, tasks: List[NestedTask])

    type NestedTask = String

    import NestedOrg._, NestedDepartment._, NestedEmployee._

    val eachDpt = departments ^|->> each
    val eachEmp = employees ^|->> each
    val eachTsk = tasks ^|->> each

    def expertise(u: String): Fold[NestedOrg, String] =
      eachDpt.asFold
             .withFilter(eachEmp.all(eachTsk.contains(u)))
             .composeLens(dpt)
  }
}

