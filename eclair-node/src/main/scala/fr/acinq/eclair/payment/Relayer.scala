package fr.acinq.eclair.payment

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import fr.acinq.bitcoin.Crypto.{PrivateKey, ripemd160, sha256}
import fr.acinq.bitcoin.{BinaryData, Crypto, MilliSatoshi, ScriptWitness, Transaction}
import fr.acinq.eclair.Globals
import fr.acinq.eclair.blockchain.WatchEventSpent
import fr.acinq.eclair.channel._
import fr.acinq.eclair.crypto.Sphinx
import fr.acinq.eclair.crypto.Sphinx.ParsedPacket
import fr.acinq.eclair.wire._
import scodec.bits.BitVector
import scodec.{Attempt, DecodeResult}

import scala.util.{Failure, Success, Try}

// @formatter:off

case class OutgoingChannel(channelId: BinaryData, channel: ActorRef, nodeAddress: BinaryData)

sealed trait Origin
case class Local(sender: ActorRef) extends Origin
case class Relayed(upstream: ActorRef, htlcIn: UpdateAddHtlc) extends Origin

case class AddHtlcSucceeded(add: UpdateAddHtlc, origin: Origin)
case class AddHtlcFailed(add: CMD_ADD_HTLC, failure: FailureMessage)
case class ForwardAdd(add: UpdateAddHtlc)
case class ForwardFulfill(fulfill: UpdateFulfillHtlc)
case class ForwardFail(fail: UpdateFailHtlc)
case class ForwardFailMalformed(fail: UpdateFailMalformedHtlc)

// @formatter:on


/**
  * Created by PM on 01/02/2017.
  */
class Relayer(nodeSecret: PrivateKey, paymentHandler: ActorRef) extends Actor with ActorLogging {

  context.system.eventStream.subscribe(self, classOf[ChannelStateChanged])
  context.system.eventStream.subscribe(self, classOf[ShortChannelIdAssigned])

  override def receive: Receive = main(Set(), Map(), Map(), Map())

  def main(channels: Set[OutgoingChannel], bindings: Map[UpdateAddHtlc, Origin], shortIds: Map[BinaryData, Long], channelUpdates: Map[Long, ChannelUpdate]): Receive = {

    case ChannelStateChanged(channel, _, remoteNodeId, _, NORMAL, d: DATA_NORMAL) =>
      import d.commitments.channelId
      log.info(s"adding channel $channelId to available channels")
      context become main(channels + OutgoingChannel(channelId, channel, remoteNodeId.hash160), bindings, shortIds, channelUpdates)

    case ChannelStateChanged(channel, _, remoteNodeId, _, NEGOTIATING, d: DATA_NEGOTIATING) =>
      import d.commitments.channelId
      log.info(s"removing channel $channelId from available channels")
      // TODO: cleanup bindings
      context become main(channels - OutgoingChannel(channelId, channel, remoteNodeId.hash160), bindings, shortIds, channelUpdates)

    case ChannelStateChanged(channel, _, remoteNodeId, _, CLOSING, d: DATA_CLOSING) =>
      import d.commitments.channelId
      log.info(s"removing channel $channelId from available channels")
      // TODO: cleanup bindings
      context become main(channels - OutgoingChannel(channelId, channel, remoteNodeId.hash160), bindings, shortIds, channelUpdates)

    case ShortChannelIdAssigned(_, channelId, shortChannelId) =>
      context become main(channels, bindings, shortIds + (channelId -> shortChannelId), channelUpdates)

    case channelUpdate: ChannelUpdate =>
      log.info(s"updating relay parameters with channelUpdate=$channelUpdate")
      context become main(channels, bindings, shortIds, channelUpdates + (channelUpdate.shortChannelId -> channelUpdate))

    case ForwardAdd(add) =>
      Try(Sphinx.parsePacket(nodeSecret, add.paymentHash, add.onionRoutingPacket))
        .map {
          case ParsedPacket(payload, nextNodeAddress, nextPacket, sharedSecret) => (LightningMessageCodecs.perHopPayloadCodec.decode(BitVector(payload.data)), nextNodeAddress, nextPacket, sharedSecret)
        } match {
        case Success((_, nextNodeAddress, _, sharedSecret)) if nextNodeAddress.forall(_ == 0) =>
          log.info(s"looks like we are the final recipient of htlc #${add.id}")
          paymentHandler forward add
        case Success((Attempt.Successful(DecodeResult(perHopPayload, _)), nextNodeAddress, nextPacket, sharedSecret)) if channels.exists(_.nodeAddress == nextNodeAddress) =>
          val outgoingChannel = channels.find(_.nodeAddress == nextNodeAddress).get
          val channelUpdate = shortIds.get(outgoingChannel.channelId).flatMap(shortId => channelUpdates.get(shortId))
          channelUpdate match {
            case None =>
              // TODO: clarify what we're supposed to to in the specs
              sender ! CMD_FAIL_HTLC(add.id, Right(TemporaryChannelFailure), commit = true)
            case Some(channelUpdate) if add.amountMsat < channelUpdate.htlcMinimumMsat =>
              sender ! CMD_FAIL_HTLC(add.id, Right(AmountBelowMinimum(add.amountMsat, channelUpdate)), commit = true)
            case Some(channelUpdate) if add.expiry != perHopPayload.outgoing_cltv_value + channelUpdate.cltvExpiryDelta =>
              sender ! CMD_FAIL_HTLC(add.id, Right(IncorrectCltvExpiry(add.expiry, channelUpdate)), commit = true)
            case Some(channelUpdate) if add.expiry < Globals.blockCount.get() + 3 =>
              // if we are the final payee, we need a reasonable amount of time to pull the funds before the sender can get refunded
              sender ! CMD_FAIL_HTLC(add.id, Right(FinalExpiryTooSoon), commit = true)
            case _ =>
              val downstream = outgoingChannel.channel
              log.info(s"forwarding htlc #${add.id} to downstream=$downstream")
              downstream forward CMD_ADD_HTLC(perHopPayload.amt_to_forward, add.paymentHash, perHopPayload.outgoing_cltv_value, nextPacket, upstream_opt = Some(add), commit = true)
          }
        case Success((Attempt.Successful(DecodeResult(_, _)), nextNodeAddress, _, sharedSecret)) =>
          log.warning(s"couldn't resolve downstream node address $nextNodeAddress, failing htlc #${add.id}")
          sender ! CMD_FAIL_HTLC(add.id, Right(UnknownNextPeer), commit = true)
        case Success((Attempt.Failure(cause), _, _, sharedSecret)) =>
          log.error(s"couldn't parse payload: $cause")
          sender ! CMD_FAIL_HTLC(add.id, Right(PermanentNodeFailure), commit = true)
        case Failure(t) =>
          log.error(t, "couldn't parse onion: ")
          // we cannot even parse the onion packet
          sender ! CMD_FAIL_MALFORMED_HTLC(add.id, Crypto.sha256(add.onionRoutingPacket), failureCode = FailureMessageCodecs.BADONION, commit = true)
      }

    case AddHtlcSucceeded(downstream, origin) =>
      origin match {
        case Local(_) => log.info(s"we are the origin of htlc ${downstream.channelId}/${downstream.id}")
        case Relayed(_, upstream) => log.info(s"relayed htlc ${upstream.channelId}/${upstream.id} to ${downstream.channelId}/${downstream.id}")
      }
      context become main(channels, bindings + (downstream -> origin), shortIds, channelUpdates)

    case AddHtlcFailed(CMD_ADD_HTLC(_, _, _, onion, Some(updateAddHtlc), _), failure) if channels.exists(_.channelId == updateAddHtlc.channelId) =>
      val upstream = channels.find(_.channelId == updateAddHtlc.channelId).get.channel
      upstream ! CMD_FAIL_HTLC(updateAddHtlc.id, Right(failure), commit = true)

    case ForwardFulfill(fulfill) =>
      bindings.find(b => b._1.channelId == fulfill.channelId && b._1.id == fulfill.id) match {
        case Some((htlcOut, Relayed(upstream, htlcIn))) =>
          upstream ! CMD_FULFILL_HTLC(htlcIn.id, fulfill.paymentPreimage, commit = true)
          context.system.eventStream.publish(PaymentRelayed(MilliSatoshi(htlcIn.amountMsat), MilliSatoshi(htlcIn.amountMsat - htlcOut.amountMsat), htlcIn.paymentHash))
          context become main(channels, bindings - htlcOut, shortIds, channelUpdates)
        case Some((htlcOut, Local(origin))) =>
          log.info(s"we were the origin payer for htlc #${fulfill.id}")
          origin ! fulfill
          context become main(channels, bindings - htlcOut, shortIds, channelUpdates)
        case None =>
          log.warning(s"no origin found for htlc ${fulfill.channelId}/${fulfill.id}")
      }

    case ForwardFail(fail) =>
      bindings.find(b => b._1.channelId == fail.channelId && b._1.id == fail.id) match {
        case Some((htlcOut, Relayed(upstream, htlcIn))) =>
          upstream ! CMD_FAIL_HTLC(htlcIn.id, Left(fail.reason), commit = true)
          context become main(channels, bindings - htlcOut, shortIds, channelUpdates)
        case Some((htlcOut, Local(origin))) =>
          log.info(s"we were the origin payer for htlc #${fail.id}")
          origin ! fail
          context become main(channels, bindings - htlcOut, shortIds, channelUpdates)
        case None =>
          log.warning(s"no origin found for htlc ${fail.channelId}/${fail.id}")
      }

    case ForwardFailMalformed(fail) =>
      bindings.find(b => b._1.channelId == fail.channelId && b._1.id == fail.id) match {
        case Some((htlcOut, Relayed(upstream, htlcIn))) =>
          upstream ! CMD_FAIL_MALFORMED_HTLC(htlcIn.id, fail.onionHash, fail.failureCode, commit = true)
          context become main(channels, bindings - htlcOut, shortIds, channelUpdates)
        case Some((htlcOut, Local(origin))) =>
          log.info(s"we were the origin payer for htlc #${fail.id}")
          origin ! fail
          context become main(channels, bindings - htlcOut, shortIds, channelUpdates)
        case None =>
          log.warning(s"no origin found for htlc ${fail.channelId}/${fail.id}")
      }

    case w@WatchEventSpent(BITCOIN_HTLC_SPENT, tx) =>
      // when a remote or local commitment tx containing outgoing htlcs is published on the network,
      // we watch it in order to extract payment preimage if funds are pulled by the counterparty
      // we can then use these preimages to fulfill origin htlcs
      log.warning(s"processing BITCOIN_HTLC_SPENT with txid=${tx.txid} tx=${Transaction.write(tx)}")
      require(tx.txIn.size == 1, s"htlc tx should only have 1 input")
      val witness = tx.txIn(0).witness
      val extracted = witness match {
        case ScriptWitness(Seq(localSig, paymentPreimage, htlcOfferedScript)) if paymentPreimage.size == 32 =>
          log.warning(s"extracted preimage=$paymentPreimage from tx=${Transaction.write(tx)} (claim-htlc-success)")
          paymentPreimage
        case ScriptWitness(Seq(BinaryData.empty, remoteSig, localSig, paymentPreimage, htlcReceivedScript)) if paymentPreimage.size == 32 =>
          log.warning(s"extracted preimage=$paymentPreimage from tx=${Transaction.write(tx)} (htlc-success)")
          paymentPreimage
        case ScriptWitness(Seq(BinaryData.empty, remoteSig, localSig, BinaryData.empty, htlcOfferedScript)) =>
          val paymentHash160 = BinaryData(htlcOfferedScript.slice(109, 109 + 20))
          log.warning(s"extracted paymentHash160=$paymentHash160 from tx=${Transaction.write(tx)} (htlc-timeout)")
          paymentHash160
        case ScriptWitness(Seq(remoteSig, BinaryData.empty, htlcReceivedScript)) =>
          val paymentHash160 = BinaryData(htlcReceivedScript.slice(69, 69 + 20))
          log.warning(s"extracted paymentHash160=$paymentHash160 from tx=${Transaction.write(tx)} (claim-htlc-timeout)")
          paymentHash160
      }
      val htlcsOut = bindings.collect {
        case b@(htlcOut, Relayed(upstream, htlcIn)) if htlcIn.paymentHash == sha256(extracted) =>
          log.warning(s"found a match between preimage=$extracted and origin htlc=$htlcIn")
          upstream ! CMD_FULFILL_HTLC(htlcIn.id, extracted, commit = true)
          htlcOut
        case b@(htlcOut, Relayed(upstream, htlcIn)) if ripemd160(htlcIn.paymentHash) == extracted =>
          log.warning(s"found a match between paymentHash160=$extracted and origin htlc=$htlcIn")
          upstream ! CMD_FAIL_HTLC(htlcIn.id, Right(PermanentChannelFailure), commit = true)
          htlcOut
      }
      context become main(channels, bindings -- htlcsOut, shortIds, channelUpdates)

    case 'channels => sender ! channels
  }
}

object Relayer {
  def props(nodeSecret: PrivateKey, paymentHandler: ActorRef) = Props(classOf[Relayer], nodeSecret: PrivateKey, paymentHandler)
}
