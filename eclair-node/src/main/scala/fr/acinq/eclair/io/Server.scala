package fr.acinq.eclair.io

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}
import akka.io.Tcp.SO.KeepAlive
import akka.io.{IO, Tcp}
import fr.acinq.eclair.NodeParams
import fr.acinq.eclair.crypto.Noise.KeyPair
import fr.acinq.eclair.crypto.TransportHandler
import fr.acinq.eclair.crypto.TransportHandler.HandshakeCompleted
import fr.acinq.eclair.wire.LightningMessage

import scala.concurrent.Promise

/**
  * Created by PM on 27/10/2015.
  */
class Server(nodeParams: NodeParams, switchboard: ActorRef, address: InetSocketAddress, bound: Option[Promise[Unit]] = None) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, address, options = KeepAlive(true) :: Nil)

  def receive() = main(Set())

  def main(transports: Set[ActorRef]): Receive = {
    case Bound(localAddress) =>
      bound.map(_.success())
      log.info(s"bound on $localAddress")

    case CommandFailed(_: Bind) =>
      bound.map(_.failure(new RuntimeException("TCP bind failed")))
      context stop self

    case Connected(remote, _) =>
      log.info(s"connected to $remote")
      val connection = sender
      val transport = context.actorOf(Props(
        new TransportHandler[LightningMessage](
          KeyPair(nodeParams.privateKey.publicKey.toBin, nodeParams.privateKey.toBin),
          None,
          connection = connection,
          serializer = LightningMessageSerializer)))
      connection ! akka.io.Tcp.Register(transport)
      context become main(transports + transport)

    case h: HandshakeCompleted =>
      switchboard ! h
  }

  // we should not restart a failing transport
  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = true) { case _ => SupervisorStrategy.Stop }
}

object Server {

  def props(nodeParams: NodeParams, switchboard: ActorRef, address: InetSocketAddress, bound: Option[Promise[Unit]] = None): Props = Props(new Server(nodeParams, switchboard, address, bound))

}

