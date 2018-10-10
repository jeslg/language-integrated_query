package org.hablapps.liq

object ListComprehension {

  case class Person(name: String, age: Int)
  case class Couple(her: String, him: String)

  type People  = List[Person]
  type Couples = List[Couple]

  val people = List(
    Person("Alex", 60), 
    Person("Bert", 55), 
    Person("Cora", 33), 
    Person("Drew", 31),
    Person("Edna", 21),
    Person("Fred", 60))

  val couples = List(
    Couple("Alex", "Bert"),
    Couple("Cora", "Drew"),
    Couple("Edna", "Fred"))

  object Primitives {

    val getPeople: People = people

    val getPeopleName: List[String] = people.map(_.name)

    val getPeopleOnTheirThirties: People =
      for {
        p <- people
        if 30 <= p.age && p.age < 40
      } yield p

    val getHerAges: List[Int] =
      for {
        w <- people
        n <- couples.map(_.her)
        if w.name == n
      } yield w.age
  }

  object QueryViaQuotation {
    
    val difference =
      for {
        c <- couples
        w <- people
        m <- people
        if c.her == w.name && c.him == m.name && w.age > m.age
      } yield (w.name, w.age - m.age)
  }

  object AbstractingOverValues {

    val range = { (a: Int, b: Int) =>
      for {
        w <- people
        if a <= w.age && w.age < b
      } yield w.name
    }

    range(30, 40)
  }

  object AbstractingOverAPredicate {

    val satisfies = { (p: Int => Boolean) =>
      for {
        w <- people
        if p(w.age)
      } yield w.name
    }

    satisfies(i => 30 <= i && i < 40)
  }

  object ComposingQueries {
    import AbstractingOverValues.range
    
    val getAge = { (s: String) =>
      for {
        u <- people
        if u.name == s
      } yield u.age
    }

    val compose = { (s: String, t: String) =>
      for {
        a1 <- getAge(s)
        a2 <- getAge(t)
        xs <- range(a1, a2)
      } yield xs
    }
  }
}

