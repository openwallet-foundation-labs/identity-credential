package org.multipaz.testapp.provisioning.backend

import io.ktor.client.HttpClient
import kotlinx.io.bytestring.ByteString
import org.multipaz.device.DeviceAssertionMaker
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.handler.NoopCipher
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import org.multipaz.rpc.handler.RpcNotifications
import org.multipaz.rpc.handler.RpcNotificationsLocal
import org.multipaz.rpc.handler.RpcNotifier
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.storage.Storage
import org.multipaz.testapp.platformHttpClientEngineFactory
import org.multipaz.testapp.platformSecureAreaProvider
import org.multipaz.testapp.platformStorage
import org.multipaz.util.fromBase64Url
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * [BackendEnvironment] implementation for running provisioning back-end locally in-app.
 */
class BackendEnvironmentLocal(
    applicationSupportProvider: () -> ApplicationSupportLocal,
    private val deviceAssertionMaker: DeviceAssertionMaker
): BackendEnvironment {
    private var configuration = ConfigurationImpl()
    private val storage = platformStorage()
    private val resources = ResourcesImpl()
    private val notificationsLocal = RpcNotificationsLocal(NoopCipher)
    private val httpClient = HttpClient(platformHttpClientEngineFactory()) {
        followRedirects = false
    }
    private val applicationSupportLocal by lazy(applicationSupportProvider)

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            RpcNotifications::class -> notificationsLocal
            RpcNotifier::class -> notificationsLocal
            HttpClient::class -> httpClient
            SecureAreaProvider::class -> platformSecureAreaProvider()
            DeviceAssertionMaker::class -> deviceAssertionMaker
            ApplicationSupport::class -> applicationSupportLocal
            RpcAuthInspector::class -> RpcAuthInspectorAssertion.Default
            else -> return null
        })
    }

    // TODO: this should interface with the testapp settings
    class ConfigurationImpl: Configuration {
        override fun getValue(key: String): String? {
            val value = when (key) {
                "developerMode" -> "true"
                "waitForNotificationSupported" -> "false"
                "androidRequireGmsAttestation" -> "false"
                "androidRequireVerifiedBootGreen" -> "false"
                "androidRequireAppSignatureCertificateDigests" -> ""
                "cloudSecureAreaUrl" -> "http://localhost:8080/server/csa"
                else -> null
            }
            return value
        }

    }

    class ResourcesImpl: Resources {
        override fun getRawResource(name: String): ByteString? {
            return when(name) {
                "generic/logo.png" -> ByteString(
                    """
                        iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8_9hAAAAAXNSR0IB2cksfwAAAARnQU1B
                        AACxjwv8YQUAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAA
                        Av1JREFUOMttk01oHHUYh5_52JnZ3ZnZ3Wx2Q7atTdvESg_qRaRCLyq1BBT0plB6EQva9qaC
                        op61F-mhBxExBw_ix0ELSomgpDQnoW2qFkI26aadppvs5-zsfM_fQ0CK7Xt74eHH74X3kXjE
                        fPvWi8fmXzr2glk1Z5Ak3PZg49Ivv__2xld_LP2flR5c_jl_-ujskUMX1b21p1FU6Hhk4QB5
                        ug45SJt3rv18aentVy9eXn4ooPvdB6cqhx__knxBpd2HMAZbA6EwclqYtg3TNvjj5Mbin28-
                        9e7CAoACMP7hk-etJw5-j1VU2eqDACYNqFlgq2hVG8KExBki27I8dXDy5Wct7co3V1fXJfHX
                        Fyod_29mpubYDKGYQa0IQkCzB7GAxyzQZXD6EKRQ8wlvd1a3mskReXzTmWeyMkc3hroFVWsX
                        WmnD3hIcrsLmELYCKNdB0qGXR9-zf-7m-q15VdO0EygKbI_BVyCfQk4Hw4aNGAoC0tLuWakK
                        5Sm4fx8akzz35DMn1KifzsbLLe7dc7FsA8vSMSp5mGlAz4dAgekG6AHspFAtkA1uIweCSqk2
                        K3eGPTWSx3hJnyyBoevRam6yfXUFYmW38iiASIZMhUhDViuQ7YPMVGShZM2d7pBqpUQ4TtAV
                        AynN4Y8iWivXCO6sgdeDcQ7kAnRdsOsQ5Qn67rpsFouLIpDI4gyUBHcQQJQjzVKED-2NHYar
                        DowScAOIJfBccNbY7jiLklheMFpLt9aKhVwjFhndjkc5X0HoIXESEQeQZQlpKtBVg4KZxwsH
                        JEniNPbXDsnS0VNBecI667kZWk5holokFSGDdkwwTIn8mHgsYRYtCqZB6AekkaBsmmdLJz8K
                        _ntl58LH76Uj_VPTzhMLHxUN1x8iyMjpKiIRZL5BlHlMTBXenzj94WcPydT-_Pxrg358wdCs
                        PZZlICkBvpsgCQl3PMQs63dr08VzyutnfnykjQDi168N53rvlTDiuJpLDiiKgqrF6_V9By6n
                        3vZP6sl3ggf5fwFR41jmmXTExgAAAABJRU5ErkJggg
                    """.trimIndent().replace("\n", "").fromBase64Url()
                    )
                "generic/card_art.png" -> ByteString(
                    """
                        iVBORw0KGgoAAAANSUhEUgAAAFAAAAAzCAMAAAA3r39rAAAAAXNSR0IB2cksfwAAAARnQU1B
                        AACxjwv8YQUAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAA
                        AwBQTFRFzNjXR3BMZ2BWf3lv1uHe3tzXyMe_7vj1qbOq1NLLwLertbStmJaNrKmhp6WeioZ8
                        0NHJv8nF2NTPdXJrj4-Jla-n3vb51PP0k6WZye7xlKmfl6ufl7SvyOrtz_P0zu_xlKib6f__
                        2vXzmrewmLKq0_Lw8f__nLu11_f4zOXilaujmK2jkbq5kriwob-5laOSkbe1y-nqiZ6Y4fX0
                        wunwnKmfk7Or2vn7pcO-Q3xux-fpSYBzvOXsxd7azuzumqidire-XI16tt_o3-_ow9rV4_v7
                        j7u_kqCWh5qR3___yeLei73G-___l6KXibO4jqOenK6qCiYbsdriwd7gm6aah7CxqsbCn7e0
                        CTAdVodzyvL1q9Tb4_Puj52QrsrGOX5ypM3TntDcka-iyuzkH0pHgJGLY5aC2_DuxuLkn8bM
                        0fDqz_f7lcPJQ1hVkaimBCcWPVBGWWpksM_MvNrZobOugJuMyM_JZ3t5GEI1YnNuQ4hzaI59
                        TmRcb56GmL7C7Pn3M0Y-iKefl8jSGYy1ttPR3-vjT4h6EoPBGkNZeYqDuvL6n6yklsvYsMK9
                        v9XSjMPQSpC1iqWUZsHuASASqLexodfjEnemdZGLE3y0grmpR2JlK4qpqLy4brXfJoGaf6mN
                        usvHqtvnU4a6fqPTicK0TYahUrvucoJ7h8nbKTs1ARoP7N70hbWXjtPjkN7wf5eZdLClcqeX
                        Ya-x0v7_DzonHE9o29buTousS5a-da_Rkb-gDGeXBhgbpq-lUVtRm6HU2OjkVqG2MFtpftDt
                        b4WGBx8q1eTuZajWX5SVJVhSHZe-s7u0b7y7lM3AdcbiCSw3Tpl-UXBym8qrUYaOpuf1FDxC
                        NmVSd-XNHS4ppfPfY67Cdr7P0drUsbStJm1vi_LavsS9Nk5SYZukuPDjE1N7kp_OYKiNVKvd
                        pa7cXYSqp8HigbXYpNO4ltvMOmuBfc7FfLK6JHqFmLTSvs_zVqHFgKC4javHYsq4lMfwV4_B
                        iZ_BdpOvZZa3MZ7WAggHcqLDYJc-mgAAAAF0Uk5TAEDm2GYAAAQSSURBVFjDrZfNjqM4FIUD
                        6MrylQORX6RehFcaqTc8R61LZj2LbOYVRqx6pNm1hHpR-2zn3GtDDDipdGuOVASI_fncH1Ph
                        dDqdBipoKIiG4u1Fpj-p7DXXeD-d9rqOkIyZ51nPx_dcf1-N8sahD_0vyZh_VD_-vVw-k-KC
                        IPqR-n7of5VoEpEuUZ8xhEt_4nejQDIDGcNyRKp6NoYCkzfGBjIeN4mspNEwkQA3xORwur4L
                        sFegGWQUGdXQm94EuRI38aYeY2UUqMQfIFprE28aKwFipA9DABDHECiE9Yj5OOCCQqozyr_t
                        BntJBRPN1T3kB6kKYg0dIf7VXB_tJbtxDVhMxMp8BUySFOj07O7anXsgovMhCo5iqLvGpmRl
                        afo8ZBuD1racBfiGRcOrzWL29paVPqd5vt2m14AyW6Iu8XSTRuBtAZqnQOPZYoJnJnPM3rLv
                        AbzdrnegjF3C2vI8x1bEzh-YtsFukjp93rKQKYtmkyyWSAf04SD3ifepi1WxIGK3PADmmfLI
                        RiCSZsQkDOSjP6tE0u03PgdqGw-yqTFCZO7ETd9Ag51AHKtw4k6A255I8r0aTBUDw6_EPc_q
                        hs6ApgCkyLsXiIKX0pRxSsyA5YDJRHtMWnySRXiFbXHQtAANlQtievVHOPVeeWGQ7jl4gxh3
                        pqmiMlDTJEBRjJo00SQP4QIOQDE8FoGpjJrWGCfJXhKPvZz6Y_oYWoHV0aEA8fDXPgmoRY8L
                        1g0qDR4SkbwXYxEnRMScgMMGqA9qwiwZZSycWlzFfxRDwCUnnEqYBWC4Ky2flsWjYX1yKBBI
                        znh-DVm91gDW2mEZLRsjQDxmtAEjEM9sw55y2jJ8A_Rp0WyEj8vKAVkMgwKD5JAPuLi-BxB9
                        WCM91h_bIJc8GvT3hW5re3QXo_HRoQCzSqmrNdPrZwhx98ngR-t6G4EEh7zX4U5scklhAbUB
                        2qyoD-X0gFWcezxoAaLHeypZROBuD30ixIk-dLXu7L48JCd-xUM3SVFcJQXwVLSoxdEg3Zc4
                        nVB7AGPFPT8gcjH_D6TAScN_RHxdfgVOE9vLS0T7DJcDI7FYFfciT2efFSg_60FE1FREfk1b
                        eOzuQCFa_3z4c56E0ioQrx4jLCqR-DcU13Pszgqs8cryhj9kINp2f93l0H9uaURXVJzjXONc
                        13yIw1l_fI7Mv-cupuN87pxrXQTuidGLfsYdtxrhaPfsDhunbZu2dQtwfs-B8kV3dtwgirZz
                        Tde0XefOHWJqcOIktKZx3G6JTeu4E-D3MQIli6vHDjO7h1oMyke7pFfuCpDrSoDyNvkGpdi6
                        51p4krZFAqybP08n_lZV1R9Q85IQbnkRfFPXLO-3jJO7vuXne318fH-ij5p_6hu45_9JHrD_
                        AO5s8B5s2goUAAAAAElFTkSuQmCC
                    """.trimIndent().replace("\n", "").fromBase64Url()
                )
                else -> return null
            }
        }

        override fun getStringResource(name: String): String? {
            return when (name) {
                "generic/tos.html" ->
                    """
                        <h2>Provisioning ${'$'}ID_NAME credential</h2>
                        <p>In the following screens, information will be collected
                          to provision <b>${'$'}ID_NAME</b> from <b>${'$'}ISSUER_NAME</b>.</p>
                        <p>The created <b>${'$'}ID_NAME</b> will be bound to this device.</p>
                    """.trimIndent()
                else -> null
            }
        }
    }
}