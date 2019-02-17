package org.hablapps.liq

import io.getquill._

object QDSL {

  val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal)
  import ctx._

  case class People(name: String, age: Int)
  case class Couples(her: String, him: String)

  object Primitives {

    val getPeople: ctx.Quoted[ctx.EntityQuery[People]] = quote {
      query[People]
    }

    ctx.run(getPeople)

    val getPeopleName = quote {
      query[People].map(_.name)
    }

    ctx.run(getPeopleName)

    val getPeopleOnTheirThirties = quote {
      for {
        p <- query[People]
        if 30 <= p.age && p.age < 40
      } yield p
    }

    def getPeopleUnderFiftyDyn(people: Quoted[EntityQuery[People]]) = quote {
      for {
        p <- people
        if 30 <= p.age && p.age < 40
      } yield p
    }

    val getPeopleUnderFifty = quote {
      for {
        p <- query[People]
        if p.age < 50
      } yield p
    }

    ctx.run(getPeopleOnTheirThirties)
    ctx.run(getPeopleUnderFiftyDyn(query[People]))
    ctx.run(getPeopleUnderFifty)


    // Basically same as query[Couples] but this excludes women without a couple
    val getHerAges = quote {
      for {
        w <- query[People]
        n <- query[Couples].map(_.her)
        if w.name == n
      } yield w.age
    }

    ctx.run(getHerAges)
  }

  object QueryViaQuotation {

    val difference = quote {
      for {
        c <- query[Couples]
        w <- query[People]
        m <- query[People]
        if c.her == w.name && c.him == m.name && w.age > m.age
      } yield (w.name, w.age - m.age)
    }

    ctx.run(difference)
  }

  object AbstractingOverValues {

    val range = quote { (a: Int, b: Int) =>
      for {
        w <- query[People]
        if a <= w.age && w.age < b
      } yield w.name
    }

    ctx.run(range(30, 40))
  }

  object AbstractingOverAPredicate {

    val satisfies = quote { (p: Int => Boolean) =>
      for {
        w <- query[People]
        if p(w.age)
      } yield w.name
    }

    ctx.run(satisfies(x => 30 <= x && x < 40))
  }

  object ComposingQueries {
    import AbstractingOverValues.range

    val getAge = quote { (s: String) =>
      for {
        u <- query[People]
        if u.name == s
      } yield u.age
    }

    val compose = quote { (s: String, t: String) =>
      for {
        a <- getAge(s)
        b <- getAge(t)
        xs <- range(a, b)
      } yield xs
    }

    ctx.run(compose("Edna", "Bert"))
  }

  object DynamicallyGeneratedQueries {
    import AbstractingOverAPredicate.satisfies

    sealed abstract class Predicate
    case class Above(x: Int) extends Predicate
    case class Below(x: Int) extends Predicate
    case class And(p1: Predicate, p2: Predicate) extends Predicate
    case class Or(p1: Predicate, p2: Predicate) extends Predicate
    case class Not(p: Predicate) extends Predicate

    // XXX: notice the explicit type signature to enable recursion
    def P(t: Predicate): Quoted[Int => Boolean] = t match {
      case Above(a) => quote { (x: Int) => a <= x }
      case Below(a) => quote { (x: Int) => x < a }
      case And(t, u) => quote { (x: Int) => P(t)(x) && P(u)(x) }
      case Or(t, u) => quote { (x: Int) => P(t)(x) || P(u)(x) }
      case Not(t) => quote { (x: Int) => !P(t)(x) }
    }

    val t0: Predicate = And(Above(30), Below(40))
    val t1: Predicate = Not(Or(Below(30), Above(40)))

    // Dynamic queries, can't be generated at compile time
    ctx.run(satisfies(P(t0)))
    ctx.run(satisfies(P(t1)))
  }

  object Nesting {

    case class Departments(dpt: String)
    case class Employees(dpt: String, emp: String)
    case class Tasks(emp: String, tsk: String)

    case class Nested(dpt: String, employees: List[(String, List[String])])

    // XXX: this isn't working, since nested queries are returning
    // `EntityQuery` instead of `List`, so it doesn't type check. Maybe there's
    // an alternative way of dealing with nested data structures, but it's not
    // that relevant right now.
    //
    // val nestedOrg = quote {
    //   for {
    //     d <- query[Departments]
    //   } yield Nested(d.dpt, for {
    //       e <- query[Employees]
    //       if e.dpt == d.dpt
    //     } yield (e.emp, for {
    //         t <- query[Tasks]
    //         if t.emp == e.emp
    //       } yield t.tsk))
    // }
  }
}

