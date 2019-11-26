package EShop.lab3

import EShop.lab2.{CartActor, CartFSM, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.FSM

class OrderManagerFSM extends FSM[State, Data] {

  import OrderManager._

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(id), Empty) =>
      sender ! Done
      val cartActor = context.system.actorOf(CartFSM.props())
      cartActor ! CartActor.AddItem(id)
      goto(Open).using(CartData(cartActor))
  }

  when(Open) {
    case Event(AddItem(id), CartData(cartActor)) =>
      sender ! Done
      cartActor ! CartActor.AddItem(id)
      stay.using(CartData(cartActor))

    case Event(RemoveItem(id), CartData(cartActor)) =>
      sender ! Done
      cartActor ! CartActor.RemoveItem(id)
      stay.using(CartData(cartActor))

    case Event(Buy, CartData(cartActor)) =>
      cartActor ! CartActor.StartCheckout
      goto(InCheckout).using(CartDataWithSender(cartActor, sender))
  }

  when(InCheckout) {
    case Event(CartActor.CheckoutStarted(checkoutActor, _), CartDataWithSender(_, reportActor)) =>
      reportActor ! Done
      stay.using(InCheckoutData(checkoutActor))

    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutData(checkoutActor)) =>
      checkoutActor ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActor ! Checkout.SelectPayment(payment)
      goto(InPayment).using(InPaymentData(sender))
  }

  when(InPayment) {
    case Event(Checkout.PaymentStarted(paymentActor), InPaymentData(reportActor)) =>
      reportActor ! Done
      stay.using(InPaymentDataWithSender(paymentActor, null))

    case Event(Pay, InPaymentDataWithSender(paymentActor, _)) =>
      paymentActor ! Payment.DoPayment
      stay.using(InPaymentDataWithSender(paymentActor, sender))

    case Event(Payment.PaymentConfirmed, InPaymentDataWithSender(_, reportActor)) =>
      reportActor ! Done
      goto(Finished).using(Empty)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay
  }

}
