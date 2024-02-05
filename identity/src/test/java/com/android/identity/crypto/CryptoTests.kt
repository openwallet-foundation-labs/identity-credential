package com.android.identity.crypto

import com.android.identity.internal.Util
import org.junit.Assert
import org.junit.Test
import kotlin.experimental.xor

class CryptoTests {

    @Test
    fun digests() {
        Assert.assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Util.toHex(Crypto.digest(Algorithm.SHA256, "".toByteArray()))
        )
        Assert.assertEquals(
            "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
            Util.toHex(Crypto.digest(Algorithm.SHA256, "Hello World".toByteArray()))
        )
        Assert.assertEquals(
            "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
            Util.toHex(Crypto.digest(Algorithm.SHA384, "".toByteArray()))
        )
        Assert.assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            Util.toHex(Crypto.digest(Algorithm.SHA512, "".toByteArray()))
        )
    }

    @Test
    fun macs() {
        // These test vectors are from a zip file that can be downloaded from
        //
        //   https://csrc.nist.gov/projects/cryptographic-algorithm-validation-program/message-authentication
        //

        // First item with L=32 and T=32
        Assert.assertEquals(
            "769f00d3e6a6cc1fb426a14a4f76c6462e6149726e0dee0ec0cf97a16605ac8b",
            Util.toHex(
                Crypto.mac(
                    Algorithm.HMAC_SHA256,
                    Util.fromHex("9779d9120642797f1747025d5b22b7ac607cab08e1758f2f3a46c8be1e25c53b8c6a8f58ffefa176"),
                    Util.fromHex("b1689c2591eaf3c9e66070f8a77954ffb81749f1b00346f9dfe0b2ee905dcc288baf4a92de3f4001dd9f44c468c3d07d6c6ee82faceafc97c2fc0fc0601719d2dcd0aa2aec92d1b0ae933c65eb06a03c9c935c2bad0459810241347ab87e9f11adb30415424c6c7f5f22a003b8ab8de54f6ded0e3ab9245fa79568451dfa258e")
                )
            )
        )

        // First item with L=48 and T=48
        Assert.assertEquals(
            "7cf5a06156ad3de5405a5d261de90275f9bb36de45667f84d08fbcb308ca8f53a419b07deab3b5f8ea231c5b036f8875",
            Util.toHex(
                Crypto.mac(
                    Algorithm.HMAC_SHA384,
                    Util.fromHex("5eab0dfa27311260d7bddcf77112b23d8b42eb7a5d72a5a318e1ba7e7927f0079dbb701317b87a3340e156dbcee28ec3a8d9"),
                    Util.fromHex("f41380123ccbec4c527b425652641191e90a17d45e2f6206cf01b5edbe932d41cc8a2405c3195617da2f420535eed422ac6040d9cd65314224f023f3ba730d19db9844c71c329c8d9d73d04d8c5f244aea80488292dc803e772402e72d2e9f1baba5a6004f0006d822b0b2d65e9e4a302dd4f776b47a972250051a701fab2b70")
                )
            )
        )

        // First item with L=64 and T=64
        Assert.assertEquals(
            "33c511e9bc2307c62758df61125a980ee64cefebd90931cb91c13742d4714c06de4003faf3c41c06aefc638ad47b21906e6b104816b72de6269e045a1f4429d4",
            Util.toHex(
                Crypto.mac(
                    Algorithm.HMAC_SHA512,
                    Util.fromHex("57c2eb677b5093b9e829ea4babb50bde55d0ad59fec34a618973802b2ad9b78e26b2045dda784df3ff90ae0f2cc51ce39cf54867320ac6f3ba2c6f0d72360480c96614ae66581f266c35fb79fd28774afd113fa5187eff9206d7cbe90dd8bf67c844e202"),
                    Util.fromHex("2423dff48b312be864cb3490641f793d2b9fb68a7763b8e298c86f42245e4540eb01ae4d2d4500370b1886f23ca2cf9701704cad5bd21ba87b811daf7a854ea24a56565ced425b35e40e1acbebe03603e35dcf4a100e57218408a1d8dbcc3b99296cfea931efe3ebd8f719a6d9a15487b9ad67eafedf15559ca42445b0f9b42e")
                )
            )
        )
    }

    @Test
    fun encrypt() {
        // These test vectors are from a zip file that can be downloaded from
        //
        //   https://csrc.nist.gov/Projects/Cryptographic-Algorithm-Validation-Program/CAVP-TESTING-BLOCK-CIPHER-MODES#GCMVS
        //
        Assert.assertEquals(
            "2ccda4a5415cb91e135c2a0f78c9b2fd" + "b36d1df9b9d5e596f83e8b7f52971cb3",
            Util.toHex(
                Crypto.encrypt(
                    Algorithm.A128GCM,
                    Util.fromHex("7fddb57453c241d03efbed3ac44e371c"),
                    Util.fromHex("ee283a3fc75575e33efd4887"),
                    Util.fromHex("d5de42b461646c255c87bd2962d3b9a2"),
                )
            )
        )

        Assert.assertEquals(
            "69482957e6be5c54882d00314e0259cf" + "191e9f29bef63a26860c1e020a21137e",
            Util.toHex(
                Crypto.encrypt(
                    Algorithm.A192GCM,
                    Util.fromHex("fbc0b4c56a714c83217b2d1bcadd2ed2e9efb0dcac6cc19f"),
                    Util.fromHex("5f4b43e811da9c470d6a9b01"),
                    Util.fromHex("d2ae38c4375954835d75b8e4c2f9bbb4"),
                )
            )
        )

        Assert.assertEquals(
            "fa4362189661d163fcd6a56d8bf0405a" + "d636ac1bbedd5cc3ee727dc2ab4a9489",
            Util.toHex(
                Crypto.encrypt(
                    Algorithm.A256GCM,
                    Util.fromHex("31bdadd96698c204aa9ce1448ea94ae1fb4a9a0b3c9d773b51bb1822666b8f22"),
                    Util.fromHex("0d18e06c7c725ac9e362e1ce"),
                    Util.fromHex("2db5168e932556f8089a0622981d017d"),
                )
            )
        )
    }

    @Test
    fun decrypt() {
        // These test vectors are from a zip file that can be downloaded from
        //
        //   https://csrc.nist.gov/Projects/Cryptographic-Algorithm-Validation-Program/CAVP-TESTING-BLOCK-CIPHER-MODES#GCMVS
        //
        Assert.assertEquals(
            "28286a321293253c3e0aa2704a278032",
            Util.toHex(
                Crypto.decrypt(
                    Algorithm.A128GCM,
                    Util.fromHex("e98b72a9881a84ca6b76e0f43e68647a"),
                    Util.fromHex("8b23299fde174053f3d652ba"),
                    Util.fromHex("5a3c1cf1985dbb8bed818036fdd5ab42" + "23c7ab0f952b7091cd324835043b5eb5"),
                )
            )
        )

        Assert.assertEquals(
            "99ae6f479b3004354ff18cd86c0b6efb",
            Util.toHex(
                Crypto.decrypt(
                    Algorithm.A192GCM,
                    Util.fromHex("7a7c5b6a8a9ab5acae34a9f6e41f19a971f9c330023c0f0c"),
                    Util.fromHex("aa4c38bf587f94f99fee77d5"),
                    Util.fromHex("132ae95bd359c44aaefa6348632cafbd" + "19d7c7d5809ad6648110f22f272e7d72"),
                )
            )
        )

        Assert.assertEquals(
            "7789b41cb3ee548814ca0b388c10b343",
            Util.toHex(
                Crypto.decrypt(
                    Algorithm.A256GCM,
                    Util.fromHex("4c8ebfe1444ec1b2d503c6986659af2c94fafe945f72c1e8486a5acfedb8a0f8"),
                    Util.fromHex("473360e0ad24889959858995"),
                    Util.fromHex("d2c78110ac7e8f107c0df0570bd7c90c" + "c26a379b6d98ef2852ead8ce83a833a7"),
                )
            )
        )
    }

    @Test
    fun encryptDecrypt() {
        val key = Util.fromHex("00000000000000000000000000000000")
        val nonce = Util.fromHex("000000000000000000000000")
        val message = "Hello World".toByteArray()
        val ciptherTextAndTag = Crypto.encrypt(Algorithm.A128GCM, key, nonce, message)
        val decryptedMessage = Crypto.decrypt(Algorithm.A128GCM, key, nonce, ciptherTextAndTag)
        Assert.assertArrayEquals(decryptedMessage, message)
    }

    @Test
    fun decryptionFailure() {
        val key = Util.fromHex("00000000000000000000000000000000")
        val nonce = Util.fromHex("000000000000000000000000")
        val message = "Hello World".toByteArray()
        val ciptherTextAndTag = Crypto.encrypt(Algorithm.A128GCM, key, nonce, message)
        // Tamper with the cipher text to induce failure.
        ciptherTextAndTag[3] = ciptherTextAndTag[8].xor(0xff.toByte())
        Assert.assertThrows(IllegalStateException::class.java) {
            Crypto.decrypt(Algorithm.A128GCM, key, nonce, ciptherTextAndTag)
        }
    }

    @Test
    fun hkdf() {
        // From https://datatracker.ietf.org/doc/html/rfc5869#appendix-A
        Assert.assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            Util.toHex(
                Crypto.hkdf(
                    Algorithm.HMAC_SHA256,
                    Util.fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
                    Util.fromHex("000102030405060708090a0b0c"),
                    Util.fromHex("f0f1f2f3f4f5f6f7f8f9"),
                    42
                )
            )
        )
    }

}