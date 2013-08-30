
import fj.data.Iteratee.IterV
import fj.data.Iteratee.Input
package object testj7 {

  import scala.language.implicitConversions

  import fj.{F, F2, P1, Unit => FJUnit}
  import fj.data.{Stream => FJStream}
  
//  type F[A, B] = FJF[A, B]
//  type F2[A, B, C] = FJF2[A, B, C]
//  type P1[A] = FJP1[A]

  
  implicit def SFToF[A, B](sf: A => B): F[A, B] = new F[A, B] {
    def f(a: A): B = sf(a)
  }

  implicit def SFFToFF[A, B, C](sff: A => B => C): F[A, F[B, C]] = new F[A, F[B, C]] {
    def f(a: A): F[B, C] = sff(a)
  }
  
  implicit def SF2ToF[A, B, C](sf: (A, B) => C): F2[A, B, C] = new F2[A, B, C] {
      def f(a: A, b: B): C = sf(a, b)
  }
  
  implicit def SF0ToP1[A](f: () => A): P1[A] = new P1[A] {
    def _1(): A = f()
  }

  implicit def StreamAsFJStream[A](xs: scala.collection.immutable.Stream[A]): FJStream[A] =
    FJStream.stream(xs: _*)
    
  implicit class UnitAsFJUnit(u: Unit) {
    def asFJ: FJUnit = FJUnit.unit()
  }
  
  implicit class RichIterV[E, A](it: IterV[E, A]) {
    def cfold[Z](done: A => Input[E] => Z)(cont: (Input[E] => IterV[E, A]) => Z): Z =
      it.fold(
        ((a: A, in: Input[E]) => done(a)(in)).tuple(),
        (fi: F[Input[E], IterV[E, A]]) => cont(in => fi.f(in))
      )
  }

  implicit class RichInput[E](in: Input[E]) {
    def capply[Z](empty: => Z)(el: => (E => Z))(eof: => Z): Z =
      in.apply(
        () => empty,
        () => new F[E, Z] { def f(e: E) = el(e) },
        () => eof
      )
  }
  
}