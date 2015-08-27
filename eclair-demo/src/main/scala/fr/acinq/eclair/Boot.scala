package fr.acinq.eclair

import akka.actor.{Props, ActorSystem}
import fr.acinq.bitcoin._
import fr.acinq.lightning._
import lightning.locktime.Locktime.Blocks
import lightning.{locktime, sha256_hash}
import org.bouncycastle.util.encoders.Hex

/**
 * Created by PM on 20/08/2015.
 */
object Boot extends App {

  val system = ActorSystem()

  val anchorInput = AnchorInput(1000L, OutPoint("bff676222800bf24bbf32f5a0fc83c4ddd5782f6ba23b4b352b3a6ddf0fe0b95", 0), SignData("76a914763518984abc129ab5825b8c14b6f4c8fa16f9c988ac", Base58Check.decode("cRqkWfx32NcfJjkutHqLrfbuY8HhTeCNLa7NLBpWy4bpk7bEYQYg")._2))

  val alice = system.actorOf(Props(new Node(Hex.decode("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Hex.decode("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), 1, Some(anchorInput))), name = "alice")
  val bob = system.actorOf(Props(new Node(Hex.decode("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"), Hex.decode("dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"), 2, None)), name = "bob")

  bob.tell(INPUT_NONE, alice)
  alice.tell(INPUT_NONE, bob)

  Thread.sleep(500)
  alice ! TxConfirmed(sha256_hash(1, 2, 3, 4), 1)
  bob ! TxConfirmed(sha256_hash(1, 2, 3, 4), 1)

  Thread.sleep(500)
  bob ! TxConfirmed(sha256_hash(1, 2, 3, 4), 2)

  Thread.sleep(1000)

  val r = sha256_hash(7, 7, 7, 7)
  val rHash = Crypto.sha256(r)

  alice ! CMD_SEND_HTLC_UPDATE(100, rHash, locktime(Blocks(4)))

  Thread.sleep(1000)
  bob ! CMD_SEND_HTLC_COMPLETE(r)

  Thread.sleep(1000)
  bob ! CMD_SEND_UPDATE(200)

  Thread.sleep(1000)
  alice ! CMD_CLOSE(0)

  Thread.sleep(1000)
  alice ! BITCOIN_CLOSE_DONE
  bob ! BITCOIN_CLOSE_DONE

}
