package org.hablapps.liq

import monocle._

object OpticsTypeclas {
  import Optics._

  trait LensHom[Alg[_], S] {
    type A
    val alg: Alg[A]
    def apply(): Lens[S, A]
  }

  trait TraversalHom[Alg[_], S] {
    type A
    val alg: Alg[A]
    def apply(): Traversal[S, A]
  }

  trait Person[S] {
    val name: Lens[S, String]
    val age:  Lens[S, Int]
  }

  trait Couple[S] {
    val her: LensHom[Person, S]
    val him: LensHom[Person, S]
  }

  object Primitives {
     
    def getPeople[S](tr: TraversalHom[Person, S]): S => List[tr.A] =
      s => tr().getAll(s)

    def getPeopleName[S](tr: TraversalHom[Person, S]): S => List[String] = {
      import tr.alg.name
      (tr() ^|-> name).getAll
    }

    def getPeopleOnTheirThirtiesFl[S](
        tr: TraversalHom[Person, S]): Fold[S, tr.A] = {
      import tr.alg.age
      tr().asFold.withFilter(p => 30 <= age.get(p) && age.get(p) < 40)
    }

    def getPeopleOnTheirThirties[S](
        tr: TraversalHom[Person, S]): S => List[tr.A] =
      s => getPeopleOnTheirThirtiesFl(tr).getAll(s)

    def getHerAges[S](tr: TraversalHom[Couple, S]): S => List[Int] = {
      import tr.alg.her, her.alg.age
      (tr() ^|-> her() ^|-> age).getAll 
    }
  }

  object QueryViaQuotation {

    def differenceFl[S](
        tr: TraversalHom[Couple, S]): Fold[S, (String, Int)] = {
      import tr.alg.{her, him}
      import her.alg.{age => herAge, name => herName}, him.alg.{age => himAge}
      (tr() ^|-> (him() ^|-> himAge) * (her() ^|-> herName * herAge) ^<-> flat)
        .asFold
        .withFilter { case (ma, _, wa) => wa > ma } 
        .withMap { case (ma, wn, wa) => (wn, wa - ma) }
    }

    def difference[S](
        tr: TraversalHom[Couple, S]): S => List[(String, Int)] = 
      differenceFl(tr).getAll

    // difference(couples)
  }

  object AbstractingOverValues {

    // XXX: If we use Couples as root this becomes a nightmare, because we
    // can't reuse optics. Thereby, I can't define the Traversal with both.
    
    def nameAgeFl[S](
        tr: TraversalHom[Person, S]): Fold[S, (String, Int)] = {
      import tr.alg.{name, age}
      (tr() ^|-> name * age).asFold
    }

    def rangeFl[S](
        tr: TraversalHom[Person, S])(
        a: Int, b: Int): Fold[S, String] =
      nameAgeFl(tr).withFilter { case (_, i) => a <= i && i < b }
                   .composeLens(fst)

    def range[S](
        tr: TraversalHom[Person, S])(
        a: Int, b: Int): S => List[String] =
      rangeFl(tr)(a, b).getAll
  }

  object AbstractingOverAPredicate {
    import AbstractingOverValues.nameAgeFl

    def satisfiesFl[S](
        tr: TraversalHom[Person, S])(
        p: Int => Boolean): Fold[S, String] =
      nameAgeFl(tr).withFilter { case (_, a) => p(a) }
                   .composeLens(fst)

    def satisfies[S](
        tr: TraversalHom[Person, S])(
        p: Int => Boolean): S => List[String] =
      satisfiesFl(tr)(p).getAll
  }

  object ComposingQueries {
    import AbstractingOverValues.rangeFl

    def getAgeFl[S](
        tr: TraversalHom[Person, S])(
        s: String): Fold[S, Int] = {
      import tr.alg.{age, name}
      tr().asFold
          .withFilter(name.get(_) == s)
          .composeLens(age)
    }

    def composeFl[S](
        tr: TraversalHom[Person, S])(
        s: String, t: String): Fold[S, String] =
      (getAgeFl(tr)(s) * getAgeFl(tr)(t)).flatMap { 
        case (a, b) => rangeFl(tr)(a, b)
      }

    def compose[S](
        tr: TraversalHom[Person, S])(
        s: String, t: String): S => List[String] =
      composeFl(tr)(s, t).getAll
  }
}

