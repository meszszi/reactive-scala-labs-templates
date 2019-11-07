package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val proxy = TestProbe()

    val parentActor = system.actorOf(Props(new Actor {
      val childActor = context.actorOf(Checkout.props(self))
      def receive = {
        case msg if sender == childActor =>
          proxy.ref.forward(msg)

        case msg =>
          childActor.forward(msg)
      }
    }))

    proxy.send(parentActor, StartCheckout)
    proxy.send(parentActor, SelectDeliveryMethod("kurier"))
    proxy.send(parentActor, SelectPayment("za pobraniem"))

    proxy.fishForMessage() {
      case _: PaymentStarted => true
    }

    proxy.send(parentActor, ReceivePayment)
    proxy.expectMsg(CartActor.CloseCheckout)
  }

}
