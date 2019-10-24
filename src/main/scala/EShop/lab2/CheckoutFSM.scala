package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab2.CheckoutFSM.Status
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM)
}

class CheckoutFSM extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  startWith(NotStarted, Uninitialized)

  private val waitingForStart: StateFunction = {
    case Event(StartCheckout, data) =>
      setTimer("checkoutTimer", ExpireCheckout, checkoutTimerDuration, false)
      goto(SelectingDelivery).using(data)
  }

  when(NotStarted) { waitingForStart }

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(deliveryMethod), data) =>
      goto(SelectingPaymentMethod).using(data)

    case Event(CancelCheckout, data) =>
      cancelTimer("checkoutTimer")
      goto(Cancelled).using(data)

    case Event(ExpireCheckout, data) =>
      goto(Cancelled).using(data)
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPayment(paymentMethod), data) =>
      cancelTimer("checkoutTimer")
      setTimer("paymentTimer", ExpirePayment, paymentTimerDuration, false)
      goto(ProcessingPayment).using(data)

    case Event(CancelCheckout, data) =>
      cancelTimer("checkoutTimer")
      goto(Cancelled).using(data)

    case Event(ExpireCheckout, data) =>
      goto(Cancelled).using(data)
  }

  when(ProcessingPayment) {
    case Event(ReceivePayment, data) =>
      cancelTimer("paymentTimer")
      goto(Closed).using(data)

    case Event(CancelCheckout, data) =>
      cancelTimer("paymentTimer")
      goto(Cancelled).using(data)

    case Event(ExpirePayment, data) =>
      goto(Cancelled).using(data)
  }

  when(Cancelled) { waitingForStart }

  when(Closed) { waitingForStart }
}
