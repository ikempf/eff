package org.atnos.eff.addon.cats.effect

import cats.implicits._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._
import org.specs2._
import org.specs2.concurrent.ExecutionEnv

import org.atnos.eff._
import cats.effect.IO
import IOEffect._
import org.atnos.eff.syntax.addon.cats.effect._
import scala.concurrent.duration._

class IOEffectSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck { def is = "io".title ^ sequential ^ s2"""

 IO effects can work as normal values                    $e1
 IO effects can be attempted                             $e2
 IO effects are stacksafe with recursion                 $e3

 Async boundaries can be introduced between computations $e4
 IO effect is stacksafe with traverseA                   $e5

"""

  type S = Fx.fx2[IO, Option]

  def e1 = {
    def action[R :_io :_option]: Eff[R, Int] = for {
      a <- ioDelay(10)
      b <- ioDelay(20)
    } yield a + b

    action[S].runOption.unsafeRunTimed(5.seconds).flatten must beSome(30)
  }

  def e2 = {
    def action[R :_io :_option]: Eff[R, Int] = for {
      a <- ioDelay(10)
      b <- ioDelay { boom; 20 }
    } yield a + b

    action[S].ioAttempt.runOption.unsafeRunTimed(5.seconds).flatten must beSome(beLeft(boomException))
  }

  def e3 = {
    type R = Fx.fx1[IO]

    def loop(i: Int): IO[Eff[R, Int]] =
      if (i == 0) IO.pure(Eff.pure(1))
      else IO.pure(ioSuspend(loop(i - 1)).map(_ + 1))

    ioSuspend(loop(1000)).unsafeRunSync must not(throwAn[Exception])
  }

  def e4 = {
    def action[R :_io :_option]: Eff[R, Int] = for {
      a <- ioDelay(10).ioShift
      b <- ioDelay(20)
    } yield a + b

    action[S].runOption.unsafeRunTimed(5.seconds).flatten must beSome(30)
  }

  def e5 = {
    val action = (1 to 5000).toList.traverseA { i =>
      if (i % 5 == 0) ioDelay(i).ioShift
      else            ioDelay(i)
    }

    action.unsafeRunAsync(_ => ()) must not(throwA[Throwable])
  }

  /**
   * HELPERS
   */
  def boom: Unit = throw boomException
  val boomException: Throwable = new Exception("boom")

  def sleepFor(duration: FiniteDuration) =
    try Thread.sleep(duration.toMillis) catch { case t: Throwable => () }

}

