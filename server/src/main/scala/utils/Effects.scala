package effects
import org.atnos.eff._
import all._
import org.atnos.eff.syntax.eff._
import org.atnos.eff.syntax.all._
import org.atnos.eff.all._
import scala.concurrent._
import scala.concurrent.duration._
import Interpret._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scala.util.control.NonFatal
import errors._

import scala.concurrent.ExecutionContext.Implicits.global

object FutureEffect {
  type Fut[A] = Future[() => A]

  def future[R, A](a: => A)(implicit m: Fut <= R): Eff[R, A] =
    send[Fut, R, A](Future(() => a))

  def fut[R, A](a: => Future[A])(implicit m: Fut <= R): Eff[R, A] =
    send[Fut, R, A](a.map(n => () => n))

  def runFuture[R <: Effects, U <: Effects, A, B](atMost: Duration)(effects: Eff[R, A])(
    implicit m: Member.Aux[Fut, R, U]): Eff[U, Throwable \/  A] = {

    val recurse = new Recurse[Fut, U, Throwable \/ A] {
      def apply[X](m: Fut[X]): X \/ Eff[U, Throwable \/ A] =
        try {
          Await.result(m.map[Throwable \/ X](x => \/.right(x())).recover[Throwable \/ X] { case t => \/.left(t) }, atMost).fold(
            t => \/-(EffMonad[U].point(t.left[A])),
            x => -\/(x)
          )
        } catch { case NonFatal(t) => \/-(EffMonad[U].point(t.left[A])) }
    }
    interpret1[R, U, Fut, A, Throwable \/ A]((a: A) => a.right[Throwable])(recurse)(effects)(m)
  }
}
