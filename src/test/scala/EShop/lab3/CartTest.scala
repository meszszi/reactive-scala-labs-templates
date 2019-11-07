package EShop.lab3

import EShop.lab2.{CartActor, CartActorTest}
import EShop.lab2.CartActor.{AddItem, CheckoutStarted, RemoveItem}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.pattern.ask
import EShop.lab2.CartActorTest.cartActorWithCartSizeResponseOnStateChange
import akka.util.Timeout

import scala.concurrent.duration._

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  implicit val timeout: Timeout = 1.second

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "add item properly" in {
    val cartActor = TestActorRef(new CartActor())
    cartActor ! AddItem("book")
    (cartActor ? CartActor.GetItems).mapTo[Seq[Any]].futureValue shouldBe Seq("book")

    cartActor ! AddItem("pencil")
    (cartActor ? CartActor.GetItems).mapTo[Seq[Any]].futureValue shouldBe Seq("book", "pencil")
  }

  it should "be empty after adding and removing the same item" in {
    val cartActor = TestActorRef(new CartActor())
    cartActor ! AddItem("book")
    cartActor ! RemoveItem("book")
    (cartActor ? CartActor.GetItems).mapTo[Seq[Any]].futureValue shouldBe Seq.empty
  }

  it should "start checkout" in {
    val cartActor = cartActorWithCartSizeResponseOnStateChange(system)
    cartActor ! AddItem("book")
    cartActor ! CartActor.StartCheckout
    fishForMessage() {
      case _: CheckoutStarted => true
      case _                  => false
    }
    expectMsg(CartActorTest.inCheckoutMsg)
  }
}
