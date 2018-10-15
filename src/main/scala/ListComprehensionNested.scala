package org.hablapps.liq

import scalaz._, Scalaz._

object ListComprehensionNested {

  case class Person(name: String, age: Int)
  case class Couple(her: Person, him: Person)

  type People = List[Person]
  type Couples = List[Couple]

  val couples = List(
    Couple(Person("Alex", 60), Person("Bert", 55)),
    Couple(Person("Cora", 33), Person("Drew", 31)),
    Couple(Person("Edna", 21), Person("Fred", 60)))

  object Primitives {
    
    // XXX: we assume all people hangs from a couple, which is a different
    // assumption from the one in `ListComprehension`.
    val getPeople: People = 
      couples >>= (c => List(c.her, c.him))

    val getPeopleName: List[String] =
      getPeople.map(_.name)

    val getPeopleOnTheirThirties: People =
      for {
        p <- getPeople
        if 30 <= p.age && p.age < 40
      } yield p

    val getHerAges: List[Int] =
      couples.map(_.her).map(_.age)
  }

  object QueryViaQuotation {
    
    val difference =
      for {
        c <- couples
        if c.her.age > c.him.age
      } yield (c.her.name, c.her.age - c.him.age)
  }

  object AbstractingOverValues {
    import Primitives.getPeople

    val range = { (a: Int, b: Int) =>
      for {
        p <- getPeople
        if a <= p.age && p.age < b
      } yield p.name
    }

    range(30, 40)
  }

  object AbstractingOverAPredicate {
    import Primitives.getPeople

    val satisfies = { (pred: Int => Boolean) =>
      for {
        p <- getPeople
        if pred(p.age)
      } yield p.name
    }

    satisfies(i => 30 <= i && i < 40)
  }

  object ComposingQueries {
    import Primitives.getPeople
    import AbstractingOverValues.range
    
    val getAge = { (s: String) =>
      for {
        u <- getPeople
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

  object Nested {
    
    case class Org(
      departments: List[Department],
      employees: List[Employee],
      tasks: List[Task])

    case class Department(dpt: String)

    case class Employee(dpt: String, emp: String)

    case class Task(emp: String, tsk: String)

    val org: Org = Org(
      departments = List(
        Department("Product"), Department("Quality"),
        Department("Research"), Department("Sales")),
      employees = List(
        Employee("Product", "Alex"),
        Employee("Product", "Bert"),
        Employee("Research", "Cora"),
        Employee("Research", "Drew"),
        Employee("Research", "Edna"),
        Employee("Sales", "Fred")),
      tasks = List(
        Task("Alex", "build"),
        Task("Bert", "build"),
        Task("Cora", "abstract"),
        Task("Cora", "build"),
        Task("Cora", "design"),
        Task("Drew", "abstract"),
        Task("Drew", "design"),
        Task("Edna", "abstract"),
        Task("Edna", "call"),
        Task("Edna", "design"),
        Task("Fred", "call")))

    def expertise(u: String): List[String] =
      for {
        d <- org.departments
        if (for {
          e <- org.employees
          if d.dpt == e.dpt && (for {
            t <- org.tasks
            if t.emp == e.emp && t.tsk == u
          } yield t).isEmpty
        } yield e).isEmpty
      } yield d.dpt

    def expertise_(u: String): List[String] =
      for {
        d <- org.departments
        if org.employees.filter(_.dpt == d.dpt).forall { e =>
          org.tasks.any(t => t.emp == e.emp && t.tsk == u)
        }
      } yield d.dpt


    /* Nested organization */

    case class NestedOrg(departments: List[NestedDepartment])

    case class NestedDepartment(dpt: String, employees: List[NestedEmployee])

    case class NestedEmployee(emp: String, tasks: List[NestedTask])

    type NestedTask = String

    def nestedOrg: NestedOrg = NestedOrg(
      org.departments.map { d =>
        NestedDepartment(d.dpt, org.employees.filter(_.dpt == d.dpt).map { e =>
          NestedEmployee(e.emp, org.tasks.filter(_.emp == e.emp).map(_.tsk))
        })
      })

    def expertise__(u: String): List[String] =
      for {
        d <- nestedOrg.departments
        if d.employees.forall(_.tasks.contains(u))
      } yield d.dpt
  }
}

