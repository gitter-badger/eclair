package fr.acinq.eclair.crypto

import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.{BinaryData, Crypto}
import fr.acinq.eclair.wire.{FailureMessage, InvalidRealm, PermanentChannelFailure, TemporaryChannelFailure}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
  * Created by fabrice on 10/01/17.
  */
@RunWith(classOf[JUnitRunner])
class SphinxSpec extends FunSuite {

  import Sphinx._
  import SphinxSpec._

  /*
  hop_shared_secret[0] = 0x53eb63ea8a3fec3b3cd433b85cd62a4b145e1dda09391b348c4e1cd36a03ea66
  hop_blinding_factor[0] = 0x2ec2e5da605776054187180343287683aa6a51b4b1c04d6dd49c45d8cffb3c36
  hop_ephemeral_pubkey[0] = 0x02eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619

  hop_shared_secret[1] = 0xa6519e98832a0b179f62123b3567c106db99ee37bef036e783263602f3488fae
  hop_blinding_factor[1] = 0xbf66c28bc22e598cfd574a1931a2bafbca09163df2261e6d0056b2610dab938f
  hop_ephemeral_pubkey[1] = 0x028f9438bfbf7feac2e108d677e3a82da596be706cc1cf342b75c7b7e22bf4e6e2

  hop_shared_secret[2] = 0x3a6b412548762f0dbccce5c7ae7bb8147d1caf9b5471c34120b30bc9c04891cc
  hop_blinding_factor[2] = 0xa1f2dadd184eb1627049673f18c6325814384facdee5bfd935d9cb031a1698a5
  hop_ephemeral_pubkey[2] = 0x03bfd8225241ea71cd0843db7709f4c222f62ff2d4516fd38b39914ab6b83e0da0

  hop_shared_secret[3] = 0x21e13c2d7cfe7e18836df50872466117a295783ab8aab0e7ecc8c725503ad02d
  hop_blinding_factor[3] = 0x7cfe0b699f35525029ae0fa437c69d0f20f7ed4e3916133f9cacbb13c82ff262
  hop_ephemeral_pubkey[3] = 0x031dde6926381289671300239ea8e57ffaf9bebd05b9a5b95beaf07af05cd43595

  hop_shared_secret[4] = 0xb5756b9b542727dbafc6765a49488b023a725d631af688fc031217e90770c328
  hop_blinding_factor[4] = 0xc96e00dddaf57e7edcd4fb5954be5b65b09f17cb6d20651b4e90315be5779205
  hop_ephemeral_pubkey[4] = 0x03a214ebd875aab6ddfd77f22c5e7311d7f77f17a169e599f157bbcdae8bf071f4
  */
  test("generate ephemereal keys and secrets") {
    val (ephkeys, sharedsecrets) = computeEphemerealPublicKeysAndSharedSecrets(sessionKey, publicKeys)
    assert(ephkeys(0) == PublicKey(BinaryData("0x02eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619")))
    assert(sharedsecrets(0) == BinaryData("0x53eb63ea8a3fec3b3cd433b85cd62a4b145e1dda09391b348c4e1cd36a03ea66"))
    assert(ephkeys(1) == PublicKey(BinaryData("0x028f9438bfbf7feac2e108d677e3a82da596be706cc1cf342b75c7b7e22bf4e6e2")))
    assert(sharedsecrets(1) == BinaryData("0xa6519e98832a0b179f62123b3567c106db99ee37bef036e783263602f3488fae"))
    assert(ephkeys(2) == PublicKey(BinaryData("0x03bfd8225241ea71cd0843db7709f4c222f62ff2d4516fd38b39914ab6b83e0da0")))
    assert(sharedsecrets(2) == BinaryData("0x3a6b412548762f0dbccce5c7ae7bb8147d1caf9b5471c34120b30bc9c04891cc"))
    assert(ephkeys(3) == PublicKey(BinaryData("0x031dde6926381289671300239ea8e57ffaf9bebd05b9a5b95beaf07af05cd43595")))
    assert(sharedsecrets(3) == BinaryData("0x21e13c2d7cfe7e18836df50872466117a295783ab8aab0e7ecc8c725503ad02d"))
    assert(ephkeys(4) == PublicKey(BinaryData("0x03a214ebd875aab6ddfd77f22c5e7311d7f77f17a169e599f157bbcdae8bf071f4")))
    assert(sharedsecrets(4) == BinaryData("0xb5756b9b542727dbafc6765a49488b023a725d631af688fc031217e90770c328"))
  }

  /*
  filler = 0x80b23adf2c3947dab3b92bb1c5f70fa32f866cc09aff45c5bc4da1ed291660791aa9a1c5d28fbf8f4ecb4bf7c0a9454c82923e24c7fd0d192ea8e2ead1f17388341f313915949b602d5de1f5cb94b77d42c7dfe84edc13cf9acf541e8243989d967e7d568f26f9209bb52d9f90bfa902f3ec6e3ae9d6215c432206cd3132b69257408002aa020f2fbae32a2d5abee0a3c9fd56329b033939cd6366fbb339aa14
  hop_filler = 0x2e86897a3ae52daba4a5940cfc305ae15e9a0f8a8ac1033a15d8a14819acab6503c9df44cdaaf30629283e3458844a44a5c4bfdebdcb15fd3edb8e286124d7b47fa7a56bcc5655d2ad9809f108f238e5
   */
  test("generate filler") {
    val (_, sharedsecrets) = computeEphemerealPublicKeysAndSharedSecrets(sessionKey, publicKeys)
    val filler = generateFiller("rho", sharedsecrets.dropRight(1), 40, 20)
    assert(filler == BinaryData("0x80b23adf2c3947dab3b92bb1c5f70fa32f866cc09aff45c5bc4da1ed291660791aa9a1c5d28fbf8f4ecb4bf7c0a9454c82923e24c7fd0d192ea8e2ead1f17388341f313915949b602d5de1f5cb94b77d42c7dfe84edc13cf9acf541e8243989d967e7d568f26f9209bb52d9f90bfa902f3ec6e3ae9d6215c432206cd3132b69257408002aa020f2fbae32a2d5abee0a3c9fd56329b033939cd6366fbb339aa14"))

    val hopFiller = generateFiller("gamma", sharedsecrets.dropRight(1), 20, 20)
    assert(hopFiller == BinaryData("0x2e86897a3ae52daba4a5940cfc305ae15e9a0f8a8ac1033a15d8a14819acab6503c9df44cdaaf30629283e3458844a44a5c4bfdebdcb15fd3edb8e286124d7b47fa7a56bcc5655d2ad9809f108f238e5"))
  }

  test("parse packet") {
    val packet: BinaryData = "0x0102eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f2836866195fcf66568cad9ad9d61c8b05ffeb2ef00bf32dbff12a026817cd410d83bd65c4fc27db8a75033c527e678028eb2b95f58d04baa9986e83030fb5577e0543f62394cca6d995cf368aface565d15e778db79b7ff43a21abbd556d1e1b33753959a62e6bcb5220cda916d603c2702554c4dc17a8896af5d54c7815efcd093a0f6bad14a4d7622df88ee7cd1fe512882beb43b81f7cf3e7e633262538a7eca5f76f8434bd2215c7dda800d49ef34caf74bb4f1064f72e28fa39de96ff993cf51e26512faa0e98fa387f517c3bc4a65f6d8ca27af1d81025a85edf9e3ea7f580854c5d3ce537b955ff216c7a88dfc0bb795bfdf053300c70a1f6f23126c83b3c96a806e0cda3c3bb5cf1c57e77e25aafd117f2c559c914797cdd27440d7d033ef987178e337a899b597a34cd6f471ce40ca44b234fa0730603b1d4ad215bce719d02b98882268551912ce1f14a3a8c968649b09fb9acf69f48046e2464536d734f705e2d4a996c176786ec437b5d0f62731b251c56f67ae4169167fab24673af60d88d3252bf38f8c1ebd85986bbe25f7f0a4399d947d5b15b7ad9050b3a4f67b0720e372a56fa2da6468ec436ca7425bfedccb4186dbbc28663941a2b5473ff853dbfce8327ffb1209940d5b3c9f8d6643d11238da66e35715b5d5410b93bb5c9ed25e7695c3d2a8073e7e5373dcbdab46c50aba3d238573b333b68bdf1c209933e75e27960fc78880e8429a88c24878c2abd541a0afd0e0c364517e0e8064a94ff1cde8885b19d60d30304c5f7bbc7afa64befbdd2095b47730fdcc6aacd6cf927b6c981efad29de9c575c8663b545dc57e7dbc1b7a09bf6584d5c5b1e748c0300903d09b8b33adf548b0f4a9198c7b0f5c4b4c0e3af9fcc9af4860a23aec993996e36aefff3157259147f0536ea64d23329178f3dd95e37e0019e5ab4654325b59544bf3caa891617b59978b3d21785d36166721358a99c90bd2b8364c475b7f8058219269e561303dfd1d84042543e85d954bcd5eeb4ff897aeb2988ed3d905b3c73377be03ef8817d8595f2596e7afa6a0aa121b176738fd578be920b9cf778bd92808e5bd3d8d0decd00bdc2560a22921f138af042f38c923693494237844c13c85b4d0235c462af39d519d4fbb0309805fff51a51d2a1675eb1be407e116535f455d966fc5a33d05d1b7b7aa4cee07f1bfff801a9bd08a9c6e730771a4bf6a646c2f9e2a90bda866a6fecb3e79981f0048309aac743972adda805075163ed26a5af81bc8fe32606fc23d362dd240c5b601b78cfb31fc350de0cacc356fd62dbf6d6fcdaed073647490573c6eca5432b566cde10369f984b036310991b8965d71c85a667ee5b1d2d043aa4e4d239fda39474626a7840708cb9b7a5311d6f187f8f485ec91e5768007db224e38ff189dd809b1604cf067fa3fd54fee9c9176396cef8664557e2550f89858071da37a96c17c2a5c1e73feeab9b09d32ba7efbc6b18f9b1c4d85abb2dfb4648e0e6b12a3dd9fe96e6e5317ffe32c7b4b3796e8599b7a4caf4ad1b980350b8e7c8544871d7702f3e8c0a265bd658389fde9850570877856e66e29e16bdd9f2a6f6106e454e531d8b7062d3086b8c61f88677bfdb5e0b8bb6654e206e2123edb48d2b18c4316ab393ae41e570e144af52a7e5d3515260b387750e205596828940376ae31e55476b7fa3d5d4abf2ab586ece8acf42c4b2ca0c4"

    val ParsedPacket(payload0, address0, packet1, sharedSecret0) = parsePacket(privKeys(0), associatedData, packet)
    val ParsedPacket(payload1, address1, packet2, sharedSecret1) = parsePacket(privKeys(1), associatedData, packet1)
    val ParsedPacket(payload2, address2, packet3, sharedSecret2) = parsePacket(privKeys(2), associatedData, packet2)
    val ParsedPacket(payload3, address3, packet4, sharedSecret3) = parsePacket(privKeys(3), associatedData, packet3)
    val ParsedPacket(payload4, address4, packet5, sharedSecret4) = parsePacket(privKeys(4), associatedData, packet4)

    assert(Seq(payload0, payload1, payload2, payload3, payload4) == payloads)
    assert(Seq(address0, address1, address2, address3, address4) == Seq(publicKeys(1).hash160, publicKeys(2).hash160, publicKeys(3).hash160, publicKeys(4).hash160, zeroes(20)))

    val (_, sharedsecrets) = computeEphemerealPublicKeysAndSharedSecrets(sessionKey, publicKeys)
    assert(Seq(sharedSecret0, sharedSecret1, sharedSecret2, sharedSecret3, sharedSecret4) == sharedsecrets)
  }

  test("generate last packet") {
    val (ephemerealPublicKeys, sharedsecrets) = computeEphemerealPublicKeysAndSharedSecrets(sessionKey, publicKeys)
    val filler = generateFiller("rho", sharedsecrets.dropRight(1), 40, 20)
    val hopFiller = generateFiller("gamma", sharedsecrets.dropRight(1), 20, 20)

    // build the last packet and apply obfuscation in reverse node order
    val nextPacket = makeNextPacket(LAST_ADDRESS, payloads(4), associatedData, ephemerealPublicKeys(4), sharedsecrets(4), LAST_PACKET, filler, hopFiller)
    val nextPacket1 = makeNextPacket(publicKeys(4).hash160, payloads(3), associatedData, ephemerealPublicKeys(3), sharedsecrets(3), nextPacket)
    val nextPacket2 = makeNextPacket(publicKeys(3).hash160, payloads(2), associatedData, ephemerealPublicKeys(2), sharedsecrets(2), nextPacket1)
    val nextPacket3 = makeNextPacket(publicKeys(2).hash160, payloads(1), associatedData, ephemerealPublicKeys(1), sharedsecrets(1), nextPacket2)
    val nextPacket4 = makeNextPacket(publicKeys(1).hash160, payloads(0), associatedData, ephemerealPublicKeys(0), sharedsecrets(0), nextPacket3)
    assert(nextPacket4 == BinaryData("0x0102eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f2836866195fcf66568cad9ad9d61c8b05ffeb2ef00bf32dbff12a026817cd410d83bd65c4fc27db8a75033c527e678028eb2b95f58d04baa9986e83030fb5577e0543f62394cca6d995cf368aface565d15e778db79b7ff43a21abbd556d1e1b33753959a62e6bcb5220cda916d603c2702554c4dc17a8896af5d54c7815efcd093a0f6bad14a4d7622df88ee7cd1fe512882beb43b81f7cf3e7e633262538a7eca5f76f8434bd2215c7dda800d49ef34caf74bb4f1064f72e28fa39de96ff993cf51e26512faa0e98fa387f517c3bc4a65f6d8ca27af1d81025a85edf9e3ea7f580854c5d3ce537b955ff216c7a88dfc0bb795bfdf053300c70a1f6f23126c83b3c96a806e0cda3c3bb5cf1c57e77e25aafd117f2c559c914797cdd27440d7d033ef987178e337a899b597a34cd6f471ce40ca44b234fa0730603b1d4ad215bce719d02b98882268551912ce1f14a3a8c968649b09fb9acf69f48046e2464536d734f705e2d4a996c176786ec437b5d0f62731b251c56f67ae4169167fab24673af60d88d3252bf38f8c1ebd85986bbe25f7f0a4399d947d5b15b7ad9050b3a4f67b0720e372a56fa2da6468ec436ca7425bfedccb4186dbbc28663941a2b5473ff853dbfce8327ffb1209940d5b3c9f8d6643d11238da66e35715b5d5410b93bb5c9ed25e7695c3d2a8073e7e5373dcbdab46c50aba3d238573b333b68bdf1c209933e75e27960fc78880e8429a88c24878c2abd541a0afd0e0c364517e0e8064a94ff1cde8885b19d60d30304c5f7bbc7afa64befbdd2095b47730fdcc6aacd6cf927b6c981efad29de9c575c8663b545dc57e7dbc1b7a09bf6584d5c5b1e748c0300903d09b8b33adf548b0f4a9198c7b0f5c4b4c0e3af9fcc9af4860a23aec993996e36aefff3157259147f0536ea64d23329178f3dd95e37e0019e5ab4654325b59544bf3caa891617b59978b3d21785d36166721358a99c90bd2b8364c475b7f8058219269e561303dfd1d84042543e85d954bcd5eeb4ff897aeb2988ed3d905b3c73377be03ef8817d8595f2596e7afa6a0aa121b176738fd578be920b9cf778bd92808e5bd3d8d0decd00bdc2560a22921f138af042f38c923693494237844c13c85b4d0235c462af39d519d4fbb0309805fff51a51d2a1675eb1be407e116535f455d966fc5a33d05d1b7b7aa4cee07f1bfff801a9bd08a9c6e730771a4bf6a646c2f9e2a90bda866a6fecb3e79981f0048309aac743972adda805075163ed26a5af81bc8fe32606fc23d362dd240c5b601b78cfb31fc350de0cacc356fd62dbf6d6fcdaed073647490573c6eca5432b566cde10369f984b036310991b8965d71c85a667ee5b1d2d043aa4e4d239fda39474626a7840708cb9b7a5311d6f187f8f485ec91e5768007db224e38ff189dd809b1604cf067fa3fd54fee9c9176396cef8664557e2550f89858071da37a96c17c2a5c1e73feeab9b09d32ba7efbc6b18f9b1c4d85abb2dfb4648e0e6b12a3dd9fe96e6e5317ffe32c7b4b3796e8599b7a4caf4ad1b980350b8e7c8544871d7702f3e8c0a265bd658389fde9850570877856e66e29e16bdd9f2a6f6106e454e531d8b7062d3086b8c61f88677bfdb5e0b8bb6654e206e2123edb48d2b18c4316ab393ae41e570e144af52a7e5d3515260b387750e205596828940376ae31e55476b7fa3d5d4abf2ab586ece8acf42c4b2ca0c4"))

    // same as above but in one single step
    // this is what clients will use
    val OnionPacket(packet, _) = makePacket(sessionKey, publicKeys, payloads, associatedData)
    assert(packet == nextPacket4)
  }

  test("generate return messages") {
    val failure = TemporaryChannelFailure
    val (ephkeys, sharedsecrets) = computeEphemerealPublicKeysAndSharedSecrets(sessionKey, publicKeys)

    // error packet created by the last node
    val error0 = createErrorPacket(sharedsecrets.last, failure)

    // error packet received by the origin node
    val error = sharedsecrets.dropRight(1).reverse.foldLeft(error0)(forwardErrorPacket)

    val Some(ErrorPacket(pubkey, failure1)) = parseErrorPacket(error, sharedsecrets.zip(publicKeys))
    assert(pubkey == publicKeys.last)
    assert(failure1 == failure)
  }

  test("last node replies with an error message") {
    // route: origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4

    // origin build the onion packet
    val OnionPacket(packet, sharedSecrets) = makePacket(sessionKey, publicKeys, payloads, associatedData)

    // each node parses and forwards the packet
    // node #0
    val ParsedPacket(payload0, address0, packet1, sharedSecret0) = parsePacket(privKeys(0), associatedData, packet)
    // node #1
    val ParsedPacket(payload1, address1, packet2, sharedSecret1) = parsePacket(privKeys(1), associatedData, packet1)
    // node #2
    val ParsedPacket(payload2, address2, packet3, sharedSecret2) = parsePacket(privKeys(2), associatedData, packet2)
    // node #3
    val ParsedPacket(payload3, address3, packet4, sharedSecret3) = parsePacket(privKeys(3), associatedData, packet3)
    // node #4
    val ParsedPacket(payload4, address4, packet5, sharedSecret4) = parsePacket(privKeys(4), associatedData, packet4)
    assert(address4 == LAST_ADDRESS)

    // node #4 want to reply with an error message
    val error = createErrorPacket(sharedSecret4, PermanentChannelFailure)

    // error sent back to 3, 2, 1 and 0
    val error1 = forwardErrorPacket(error, sharedSecret3)
    val error2 = forwardErrorPacket(error1, sharedSecret2)
    val error3 = forwardErrorPacket(error2, sharedSecret1)
    val error4 = forwardErrorPacket(error3, sharedSecret0)

    // origin parses error packet and can see that it comes from node #4
    val Some(ErrorPacket(pubkey, failure)) = parseErrorPacket(error4, sharedSecrets)
    assert(pubkey == publicKeys(4))
    assert(failure == PermanentChannelFailure)
  }

  test("intermediate node replies with an error message") {
    // route: origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4

    // origin build the onion packet
    val OnionPacket(packet, sharedSecrets) = makePacket(sessionKey, publicKeys, payloads, associatedData)

    // each node parses and forwards the packet
    // node #0
    val ParsedPacket(payload0, address0, packet1, sharedSecret0) = parsePacket(privKeys(0), associatedData, packet)
    // node #1
    val ParsedPacket(payload1, address1, packet2, sharedSecret1) = parsePacket(privKeys(1), associatedData, packet1)
    // node #2
    val ParsedPacket(payload2, address2, packet3, sharedSecret2) = parsePacket(privKeys(2), associatedData, packet2)

    // node #2 want to reply with an error message
    val error = createErrorPacket(sharedSecret2, InvalidRealm)

    // error sent back to 1 and 0
    val error1 = forwardErrorPacket(error, sharedSecret1)
    val error2 = forwardErrorPacket(error1, sharedSecret0)

    // origin parses error packet and can see that it comes from node #2
    val Some(ErrorPacket(pubkey, failure)) = parseErrorPacket(error2, sharedSecrets)
    assert(pubkey == publicKeys(2))
    assert(failure == InvalidRealm)
  }
}

object SphinxSpec {
  val privKeys = Seq(
    PrivateKey(BinaryData("0x4141414141414141414141414141414141414141414141414141414141414141"), compressed = true),
    PrivateKey(BinaryData("0x4242424242424242424242424242424242424242424242424242424242424242"), compressed = true),
    PrivateKey(BinaryData("0x4343434343434343434343434343434343434343434343434343434343434343"), compressed = true),
    PrivateKey(BinaryData("0x4444444444444444444444444444444444444444444444444444444444444444"), compressed = true),
    PrivateKey(BinaryData("0x4545454545454545454545454545454545454545454545454545454545454545"), compressed = true)
  )
  val publicKeys = privKeys.map(_.publicKey)
  assert(publicKeys == Seq(
    PublicKey(BinaryData("0x02eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619")),
    PublicKey(BinaryData("0x0324653eac434488002cc06bbfb7f10fe18991e35f9fe4302dbea6d2353dc0ab1c")),
    PublicKey(BinaryData("0x027f31ebc5462c1fdce1b737ecff52d37d75dea43ce11c74d25aa297165faa2007")),
    PublicKey(BinaryData("0x032c0b7cf95324a07d05398b240174dc0c2be444d96b159aa6c7f7b1e668680991")),
    PublicKey(BinaryData("0x02edabbd16b41c8371b92ef2f04c1185b4f03b6dcd52ba9b78d9d7c89c8f221145"))
  ))

  val sessionKey: PrivateKey = PrivateKey(BinaryData("0x4141414141414141414141414141414141414141414141414141414141414141"), compressed = true)
  val payloads = Seq(
    BinaryData("0x4141414141414141414141414141414141414141"),
    BinaryData("0x4141414141414141414141414141414141414141"),
    BinaryData("0x4141414141414141414141414141414141414141"),
    BinaryData("0x4141414141414141414141414141414141414141"),
    BinaryData("0x4141414141414141414141414141414141414141")
  )
  val associatedData: BinaryData = "0x4242424242424242424242424242424242424242424242424242424242424242"
}
