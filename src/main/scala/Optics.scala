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

  implicit class TraversalOps[S, A](tr: Traversal[S, A]) {

    def contains(a: A): S => Boolean =
      tr.exist(_ == a)

    def filter(p: A => Boolean): S => List[A] =
      tr.foldMap[List[A]](a => if (p(a)) List(a) else List.empty)

    def filterMap[B](p: A => (Boolean, B)): S => List[B] =
      s => filter(a => p(a)._1)(s).map(a => p(a)._2)

    def filterMap[B](p: A => Boolean)(f: A => B): S => List[B] =
      filterMap(a => (p(a), f(a)))

    def filterContent(p: A => Boolean): Fold[S, A] =
      new Fold[S, A] {
        def foldMap[M: Monoid](f: A => M)(s: S): M = ???
      }
  }

  implicit class LensOps[S, A](ln: Lens[S, A]) {
    // XXX: unlawful when `A` and `B` are pointing to the same value
    def *[B](other: Lens[S, B]): Lens[S, (A, B)] = 
      Lens[S, (A, B)](
        s => (ln.get(s), other.get(s)))(
        ab => s => other.set(ab._2)(ln.set(ab._1)(s)))
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

    val getPeopleOnTheirThirties: People => People =
      people.filter(p => 30 <= p.age && p.age < 40)

    val getHerAges: Couples => List[Int] =
      (all ^|-> her ^|-> age).getAll
  }

  object QueryViaQuotation {
    
    val difference: Couples => List[(String, Int)] =
      all.filterMap { c =>
        (c.her.age > c.him.age, (c.her.name, c.her.age - c.him.age))
      }

    val difference2: Couples => List[(String, Int)] =
      (all ^|-> (him ^|-> age) * (her ^|-> name * age)).filterMap {
        case (ma, (wn, wa)) => (wa > ma, (wn, wa - ma))
      }
  }

  object AbstractingOverValues {
    import Primitives.coupledPeople

    val range = { (a: Int, b: Int) =>
      coupledPeople.filterMap { p =>
        (a <= p.age && p.age < b, p.name)
      }
    }

    val nameAge = coupledPeople ^|-> name * age

    val range2: (Int, Int) => Couples => List[String] = { (a, b) =>
      nameAge.filterMap { case (s, i) =>
        (a <= i && i < b, s)
      }
    }

    range(30, 40)
  }

  object AbstractingOverAPredicate {
    import AbstractingOverValues.nameAge

    val satisfies = { (pred: Int => Boolean) =>
      (all ^|->> both).filterMap(p => (pred(p.age), p.name))
    }

    val satisfies2: (Int => Boolean) => Couples => List[String] = { p =>
      nameAge.filterMap { case (n, a) => (p(a), n) }
    }

    satisfies(i => 30 <= i && i < 40)
  }

  object ComposingQueries {
    import AbstractingOverValues.range
    import AbstractingOverValues.nameAge
    
    val getAge = { (s: String) =>
      (all ^|->> both).filterMap(p => (p.name == s, p.age))
    }

    val getAge2: String => Couples => List[Int] = { (s: String) =>
      nameAge.filterMap { case (n, a) => (n == s, a) }
    }

    // XXX: filtering by content should be safe for `Fold`s. Check it!
    def getAge3(s: String): Fold[Couples, Int] =
      all.composeTraversal(both).filterContent(_.name == s).composeLens(age)

    // XXX: we should be composing optics instead of Options, this isn't valid!
    val compose = { (s: String, t: String) => (c: Couples) =>
      (getAge(s)(c).headOption |@| getAge(t)(c).headOption)(range(_, _))
    }

    val compose2 = { (s: String, t: String) => (c: Couples) =>
      (getAge3(s).headOption(c) |@| getAge3(t).headOption(c))(range(_, _))
    }
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

    // XXX: we use optics, but we don't compose them. So weird!
    def expertise(u: String): NestedOrg => List[String] =
      eachDpt.filterMap(eachEmp.all(eachTsk.contains(u)))(_.dpt)
  }
}

