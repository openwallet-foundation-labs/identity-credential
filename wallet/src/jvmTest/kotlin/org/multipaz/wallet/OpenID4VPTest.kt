package org.multipaz_credential.wallet

import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.javaPrivateKey
import org.multipaz.crypto.javaPublicKey
import org.multipaz.document.DocumentRequest
import org.multipaz.issuance.CredentialFormat
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.util.toBase64Url
import org.multipaz_credential.wallet.presentation.DescriptorMap
import org.multipaz_credential.wallet.presentation.createPresentationSubmission
import org.multipaz_credential.wallet.presentation.formatAsDocumentRequest
import org.multipaz_credential.wallet.presentation.getAuthRequestFromJwt
import org.multipaz_credential.wallet.presentation.parsePathItem
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
import org.junit.Test
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.time.Duration

class OpenID4VPTest {

    // real-life example from https://verifier.eudiw.dev/home
    private val eudiAgeOver18RequestObject = "eyJ4NWMiOlsiTUlJREtqQ0NBckNnQXdJQkFnSVVmeTl1NlNMdGdOdWY5UFhZYmgvUURxdVh6NTB3Q2dZSUtvWkl6ajBFQXdJd1hERWVNQndHQTFVRUF3d1ZVRWxFSUVsemMzVmxjaUJEUVNBdElGVlVJREF4TVMwd0t3WURWUVFLRENSRlZVUkpJRmRoYkd4bGRDQlNaV1psY21WdVkyVWdTVzF3YkdWdFpXNTBZWFJwYjI0eEN6QUpCZ05WQkFZVEFsVlVNQjRYRFRJME1ESXlOakF5TXpZek0xb1hEVEkyTURJeU5UQXlNell6TWxvd2FURWRNQnNHQTFVRUF3d1VSVlZFU1NCU1pXMXZkR1VnVm1WeWFXWnBaWEl4RERBS0JnTlZCQVVUQXpBd01URXRNQ3NHQTFVRUNnd2tSVlZFU1NCWFlXeHNaWFFnVW1WbVpYSmxibU5sSUVsdGNHeGxiV1Z1ZEdGMGFXOXVNUXN3Q1FZRFZRUUdFd0pWVkRCWk1CTUdCeXFHU000OUFnRUdDQ3FHU000OUF3RUhBMElBQk1iV0JBQzFHaitHRE8veUNTYmdiRndwaXZQWVdMekV2SUxOdGRDdjdUeDFFc3hQQ3hCcDNEWkI0RklyNEJsbVZZdEdhVWJvVklpaFJCaVFEbzNNcFdpamdnRkJNSUlCUFRBTUJnTlZIUk1CQWY4RUFqQUFNQjhHQTFVZEl3UVlNQmFBRkxOc3VKRVhITmVrR21ZeGgwTGhpOEJBekpVYk1DVUdBMVVkRVFRZU1CeUNHblpsY21sbWFXVnlMV0poWTJ0bGJtUXVaWFZrYVhjdVpHVjJNQklHQTFVZEpRUUxNQWtHQnlpQmpGMEZBUVl3UXdZRFZSMGZCRHd3T2pBNG9EYWdOSVl5YUhSMGNITTZMeTl3Y21Wd2NtOWtMbkJyYVM1bGRXUnBkeTVrWlhZdlkzSnNMM0JwWkY5RFFWOVZWRjh3TVM1amNtd3dIUVlEVlIwT0JCWUVGRmdtQWd1QlN2U25tNjhaem81SVN0SXYyZk0yTUE0R0ExVWREd0VCL3dRRUF3SUhnREJkQmdOVkhSSUVWakJVaGxKb2RIUndjem92TDJkcGRHaDFZaTVqYjIwdlpYVXRaR2xuYVhSaGJDMXBaR1Z1ZEdsMGVTMTNZV3hzWlhRdllYSmphR2wwWldOMGRYSmxMV0Z1WkMxeVpXWmxjbVZ1WTJVdFpuSmhiV1YzYjNKck1Bb0dDQ3FHU000OUJBTUNBMmdBTUdVQ01RREdmZ0xLbmJLaGlPVkYzeFNVMGFlanUvbmVHUVVWdU5ic1F3MExlRER3SVcrckxhdGViUmdvOWhNWERjM3dybFVDTUFJWnlKN2xSUlZleU1yM3dqcWtCRjJsOVliMHdPUXBzblpCQVZVQVB5STV4aFdYMlNBYXpvbTJKanNOL2FLQWtRPT0iLCJNSUlESFRDQ0FxT2dBd0lCQWdJVVZxamd0SnFmNGhVWUprcWRZemkrMHh3aHdGWXdDZ1lJS29aSXpqMEVBd013WERFZU1Cd0dBMVVFQXd3VlVFbEVJRWx6YzNWbGNpQkRRU0F0SUZWVUlEQXhNUzB3S3dZRFZRUUtEQ1JGVlVSSklGZGhiR3hsZENCU1pXWmxjbVZ1WTJVZ1NXMXdiR1Z0Wlc1MFlYUnBiMjR4Q3pBSkJnTlZCQVlUQWxWVU1CNFhEVEl6TURrd01URTRNelF4TjFvWERUTXlNVEV5TnpFNE16UXhObG93WERFZU1Cd0dBMVVFQXd3VlVFbEVJRWx6YzNWbGNpQkRRU0F0SUZWVUlEQXhNUzB3S3dZRFZRUUtEQ1JGVlVSSklGZGhiR3hsZENCU1pXWmxjbVZ1WTJVZ1NXMXdiR1Z0Wlc1MFlYUnBiMjR4Q3pBSkJnTlZCQVlUQWxWVU1IWXdFQVlIS29aSXpqMENBUVlGSzRFRUFDSURZZ0FFRmc1U2hmc3hwNVIvVUZJRUtTM0wyN2R3bkZobmpTZ1VoMmJ0S09RRW5mYjNkb3llcU1BdkJ0VU1sQ2xoc0YzdWVmS2luQ3cwOE5CMzFyd0MrZHRqNlgvTEUzbjJDOWpST0lVTjhQcm5sTFM1UXM0UnM0WlU1T0lnenRvYU84RzlvNElCSkRDQ0FTQXdFZ1lEVlIwVEFRSC9CQWd3QmdFQi93SUJBREFmQmdOVkhTTUVHREFXZ0JTemJMaVJGeHpYcEJwbU1ZZEM0WXZBUU15Vkd6QVdCZ05WSFNVQkFmOEVEREFLQmdncmdRSUNBQUFCQnpCREJnTlZIUjhFUERBNk1EaWdOcUEwaGpKb2RIUndjem92TDNCeVpYQnliMlF1Y0d0cExtVjFaR2wzTG1SbGRpOWpjbXd2Y0dsa1gwTkJYMVZVWHpBeExtTnliREFkQmdOVkhRNEVGZ1FVczJ5NGtSY2MxNlFhWmpHSFF1R0x3RURNbFJzd0RnWURWUjBQQVFIL0JBUURBZ0VHTUYwR0ExVWRFZ1JXTUZTR1VtaDBkSEJ6T2k4dloybDBhSFZpTG1OdmJTOWxkUzFrYVdkcGRHRnNMV2xrWlc1MGFYUjVMWGRoYkd4bGRDOWhjbU5vYVhSbFkzUjFjbVV0WVc1a0xYSmxabVZ5Wlc1alpTMW1jbUZ0WlhkdmNtc3dDZ1lJS29aSXpqMEVBd01EYUFBd1pRSXdhWFVBM2orK3hsL3RkRDc2dFhFV0Npa2ZNMUNhUno0dnpCQzdOUzB3Q2RJdEtpejZIWmVWOEVQdE5DbnNmS3BOQWpFQXFyZGVLRG5yNUt3ZjhCQTd0QVRlaHhObE9WNEhuYzEwWE8xWFVMdGlnQ3diNDlScGtxbFMySHVsK0RwcU9iVXMiXSwidHlwIjoib2F1dGgtYXV0aHotcmVxK2p3dCIsImFsZyI6IkVTMjU2In0.eyJyZXNwb25zZV91cmkiOiJodHRwczovL3ZlcmlmaWVyLWJhY2tlbmQuZXVkaXcuZGV2L3dhbGxldC9kaXJlY3RfcG9zdCIsImNsaWVudF9pZF9zY2hlbWUiOiJ4NTA5X3Nhbl9kbnMiLCJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJub25jZSI6IjliNGYwNGEzLTgzMjgtNGE4Ny1iMGYxLTIzNTBmNjJkNDczNiIsImNsaWVudF9pZCI6InZlcmlmaWVyLWJhY2tlbmQuZXVkaXcuZGV2IiwicmVzcG9uc2VfbW9kZSI6ImRpcmVjdF9wb3N0Lmp3dCIsImF1ZCI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUvdjIiLCJzY29wZSI6IiIsInByZXNlbnRhdGlvbl9kZWZpbml0aW9uIjp7ImlkIjoiMzJmNTQxNjMtNzE2Ni00OGYxLTkzZDgtZmYyMTdiZGIwNjUzIiwiaW5wdXRfZGVzY3JpcHRvcnMiOlt7ImlkIjoiZXUuZXVyb3BhLmVjLmV1ZGl3LnBpZC4xIiwibmFtZSI6IkVVREkgUElEIiwicHVycG9zZSI6IldlIG5lZWQgdG8gdmVyaWZ5IHlvdXIgaWRlbnRpdHkiLCJmb3JtYXQiOnsibXNvX21kb2MiOnsiYWxnIjpbIkVTMjU2IiwiRVMzODQiLCJFUzUxMiIsIkVkRFNBIiwiRVNCMjU2IiwiRVNCMzIwIiwiRVNCMzg0IiwiRVNCNTEyIl19fSwiY29uc3RyYWludHMiOnsiZmllbGRzIjpbeyJwYXRoIjpbIiRbJ2V1LmV1cm9wYS5lYy5ldWRpdy5waWQuMSddWydhZ2Vfb3Zlcl8xOCddIl0sImludGVudF90b19yZXRhaW4iOmZhbHNlfV19fV19LCJzdGF0ZSI6IjQ0elo3Rm1yTTRuU3VvNWNKb1FYMkd4MXBLaW9Bd1ZiTl9nT2FTT1IxTi1HR2kySmZDa3k2NDFSMVVzU0pUNmJLMkFMTzR6VVE3U09WeWMwbWR5NWt3IiwiaWF0IjoxNzE2MjIxMDExLCJjbGllbnRfbWV0YWRhdGEiOnsiYXV0aG9yaXphdGlvbl9lbmNyeXB0ZWRfcmVzcG9uc2VfYWxnIjoiRUNESC1FUyIsImF1dGhvcml6YXRpb25fZW5jcnlwdGVkX3Jlc3BvbnNlX2VuYyI6IkExMjhDQkMtSFMyNTYiLCJpZF90b2tlbl9lbmNyeXB0ZWRfcmVzcG9uc2VfYWxnIjoiUlNBLU9BRVAtMjU2IiwiaWRfdG9rZW5fZW5jcnlwdGVkX3Jlc3BvbnNlX2VuYyI6IkExMjhDQkMtSFMyNTYiLCJqd2tzX3VyaSI6Imh0dHBzOi8vdmVyaWZpZXItYmFja2VuZC5ldWRpdy5kZXYvd2FsbGV0L2phcm0vNDR6WjdGbXJNNG5TdW81Y0pvUVgyR3gxcEtpb0F3VmJOX2dPYVNPUjFOLUdHaTJKZkNreTY0MVIxVXNTSlQ2YksyQUxPNHpVUTdTT1Z5YzBtZHk1a3cvandrcy5qc29uIiwic3ViamVjdF9zeW50YXhfdHlwZXNfc3VwcG9ydGVkIjpbInVybjppZXRmOnBhcmFtczpvYXV0aDpqd2stdGh1bWJwcmludCJdLCJpZF90b2tlbl9zaWduZWRfcmVzcG9uc2VfYWxnIjoiUlMyNTYifX0.xLzRWy-mPHPC3oaczv71R1eaz_7kumUVC1CIx3wpt2v6HjZa6vQIhyISGGNgdqANBvAs-xiSXzFYp-zcVOQCDQ"
    // the request object in ISO 18013-7 Annex B
    private val annexBRequestObject = "eyJ4NWMiOlsiTUlJQ1B6Q0NBZVdnQXdJQkFnSVVEbUJYeDcrMTlLaHdqbHREYkJXNEJFMENSUkV3Q2dZSUtvWkl6ajBFQXdJd2FURUxNQWtHIEExVUVCaE1DVlZReER6QU5CZ05WQkFnTUJsVjBiM0JwWVRFTk1Bc0dBMVVFQnd3RVEybDBlVEVTTUJBR0ExVUVDZ3dKUVVOTlIgU0JEYjNKd01SQXdEZ1lEVlFRTERBZEpWQ0JFWlhCME1SUXdFZ1lEVlFRRERBdGxlR0Z0Y0d4bExtTnZiVEFlRncweU16RXdNRCBNeE5EUTVNemhhRncweU5EQTVNak14TkRRNU16aGFNR2t4Q3pBSkJnTlZCQVlUQWxWVU1ROHdEUVlEVlFRSURBWlZkRzl3YVdFIHhEVEFMQmdOVkJBY01CRU5wZEhreEVqQVFCZ05WQkFvTUNVRkRUVVVnUTI5eWNERVFNQTRHQTFVRUN3d0hTVlFnUkdWd2RERVUgTUJJR0ExVUVBd3dMWlhoaGJYQnNaUzVqYjIwd1dUQVRCZ2NxaGtqT1BRSUJCZ2dxaGtqT1BRTUJCd05DQUFSZkxoK2NXWHE1ZiBXUmY5Q3dvOFZSa3A5QUFPT0xhUDNVQ2kzWVkxVkRISEV4N2xBbjlNQ1hvL3ZuaXFMODhWRkVpMVB0VDlPRGFJTlZJWFpGRmpPIHJZbzJzd2FUQWRCZ05WSFE0RUZnUVV4djZIdFJRazlxN0FTUUNVcU9xRXVuNVM4UVF3SHdZRFZSMGpCQmd3Rm9BVXh2Nkh0UlEgazlxN0FTUUNVcU9xRXVuNVM4UVF3RHdZRFZSMFRBUUgvQkFVd0F3RUIvekFXQmdOVkhSRUVEekFOZ2d0bGVHRnRjR3hsTG1OdiBiVEFLQmdncWhrak9QUVFEQWdOSUFEQkZBaUJ0NS9tYWl4SnlhV05LRzhXOWRBZVBodmhoNU9IanN3SmFFamN5WWlxb29nSWhBIE53VEdUZGcxMlJFelFNZlFTWFRTVnROcDFqakpNUHNpcHFSN2tJSzFKZFQiXSwidHlwIjoiSldUIiwiYWxnIjoiRVMyNTYifQ.eyJhdWQiOiJodHRwczovL3NlbGYtaXNzdWVkLm1lL3YyIiwicmVzcG9uc2VfdHlwZSI6InZwX3Rva2VuIiwicHJlc2VudGF0aW9uX2RlZmluaXRpb24iOnsiaWQiOiJtREwtc2FtcGxlLXJlcSIsImlucHV0X2Rlc2NyaXB0b3JzIjpbeyJpZCI6Im9yZy5pc28uMTgwMTMuNS4xLm1ETCAiLCJmb3JtYXQiOnsibXNvX21kb2MiOnsiYWxnIjpbIkVTMjU2IiwiRVMzODQiLCJFUzUxMiIsIkVkRFNBIiwiRVNCMjU2IiwiRVNCMzIwIiwiRVNCMzg0IiwiRVNCNTEyIl19fSwiY29uc3RyYWludHMiOnsiZmllbGRzIjpbeyJwYXRoIjpbIiRbJ29yZy5pc28uMTgwMTMuNS4xJ11bJ2JpcnRoX2RhdGUnXSJdLCJpbnRlbnRfdG9fcmV0YWluIjpmYWxzZX0seyJwYXRoIjpbIiRbJ29yZy5pc28uMTgwMTMuNS4xJ11bJ2RvY3VtZW50X251bWJlciddIl0sImludGVudF90b19yZXRhaW4iOmZhbHNlfSx7InBhdGgiOlsiJFsnb3JnLmlzby4xODAxMy41LjEnXVsnZHJpdmluZ19wcml2aWxlZ2VzJ10iXSwiaW50ZW50X3RvX3JldGFpbiI6ZmFsc2V9LHsicGF0aCI6WyIkWydvcmcuaXNvLjE4MDEzLjUuMSddWydleHBpcnlfZGF0ZSddIl0sImludGVudF90b19yZXRhaW4iOmZhbHNlfSx7InBhdGgiOlsiJFsnb3JnLmlzby4xODAxMy41LjEnXVsnZmFtaWx5X25hbWUnXSJdLCJpbnRlbnRfdG9fcmV0YWluIjpmYWxzZX0seyJwYXRoIjpbIiRbJ29yZy5pc28uMTgwMTMuNS4xJ11bJ2dpdmVuX25hbWUnXSJdLCJpbnRlbnRfdG9fcmV0YWluIjpmYWxzZX0seyJwYXRoIjpbIiRbJ29yZy5pc28uMTgwMTMuNS4xJ11bJ2lzc3VlX2RhdGUnXSJdLCJpbnRlbnRfdG9fcmV0YWluIjpmYWxzZX0seyJwYXRoIjpbIiRbJ29yZy5pc28uMTgwMTMuNS4xJ11bJ2lzc3VpbmdfYXV0aG9yaXR5J10iXSwiaW50ZW50X3RvX3JldGFpbiI6ZmFsc2V9LHsicGF0aCI6WyIkWydvcmcuaXNvLjE4MDEzLjUuMSddWydpc3N1aW5nX2NvdW50cnknXSJdLCJpbnRlbnRfdG9fcmV0YWluIjpmYWxzZX0seyJwYXRoIjpbIiRbJ29yZy5pc28uMTgwMTMuNS4xJ11bJ3BvcnRyYWl0J10iXSwiaW50ZW50X3RvX3JldGFpbiI6ZmFsc2V9LHsicGF0aCI6WyIkWydvcmcuaXNvLjE4MDEzLjUuMSddWyd1bl9kaXN0aW5ndWlzaGluZ19zaWduJ10iXSwiaW50ZW50X3RvX3JldGFpbiI6ZmFsc2V9XSwibGltaXRfZGlzY2xvc3VyZSI6InJlcXVpcmVkIn19XX0sImNsaWVudF9tZXRhZGF0YSI6eyJqd2tzIjp7ImtleXMiOlt7Imt0eSI6IkVDIiwidXNlIjoiZW5jIiwiY3J2IjoiUC0yNTYiLCJ4IjoieFZMdFphUFBLLXh2cnVoMWZFQ2xOVlRSNlJDWkJzUWFpMi1Ecm55S2t4ZyIsInkiOiItNS1RdEZxSnFHd09qRUwzVXQ4OW5yRTBNZWFVcDVSb3prc0tIcEJpeXcwIiwiYWxnIjoiRUNESC1FUyIsImtpZCI6IlA4cDB2aXJSbGg2ZkFraDUtWVNlSHQ0RUl2LWhGR25lWWsxNGQ4REY1MXcifV19LCJhdXRob3JpemF0aW9uX2VuY3J5cHRlZF9yZXNwb25zZV9hbGciOiJFQ0RILUVTIiwiYXV0aG9yaXphdGlvbl9lbmNyeXB0ZWRfcmVzcG9uc2VfZW5jIjoiQTI1NkdDTSIsInZwX2Zvcm1hdHMiOnsibXNvX21kb2MiOnsiYWxnIjpbIkVTMjU2IiwiRVMzODQiLCJFUzUxMiIsIkVkRFNBIiwiRVNCMjU2IiwiRVNCMzIwIiwiRVNCMzg0IiwiRVNCNTEyIl19fX0sInN0YXRlIjoiMzRhc2ZkMzRfMzQkMzQiLCJub25jZSI6IlNhZmRhZXKnJDQ1XzMzNDIiLCJjbGllbnRfaWQiOiJleGFtcGxlLmNvbSAiLCJjbGllbnRfaWRfc2NoZW1lIjoieDUwOV9zYW5fZG5zIiwicmVzcG9uc2VfbW9kZSI6ImRpcmVjdF9wb3N0Lmp3dCIsInJlc3BvbnNlX3VyaSI6Imh0dHBzOi8vZXhhbXBsZS5jb20vMTIzNDUvcmVzcG9uc2UifQ.DIEllOaSydngto5RYP-W5eWifqcqylKuRXYoZwtSo8ekWPkTE1_IfabbpkYCS9Y42HuAbuKiVQCN2OKAyabEwA"
    // Test example created by our verifier servlet.
    private val sdjwtRequestObject = "eyJ4NWMiOlsiTUlJQkZqQ0J2YUFEQWdFQ0FnRUJNQW9HQ0NxR1NNNDlCQU1DTUJVe" +
            "EV6QVJCZ05WQkFNTUNsSmxZV1JsY2lCTFpYa3dIaGNOTWpRd09ERXlNak0wT0RNMldoY05NelF3T0RFeU1qT" +
            "TBPRE0yV2pBVk1STXdFUVlEVlFRRERBcFNaV0ZrWlhJZ1MyVjVNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6a" +
            "jBEQVFjRFFnQUV6bXNVckdCM3NYWjFBTGh2VWRyVXdUNDkyNktHVVV2MXI0TDFPdjBEM2lfQzFNN0p0c0RuT" +
            "TYzS2V2czVXTU91QVpTUkRKaUJneS16OFFsMXdtUXh4akFLQmdncWhrak9QUVFEQWdOSUFEQkZBaUVBN1Vjc" +
            "WRqRjlINUROM1VELWdjdy1JM0Q1cVBxc3ltNGF1akhRMWhBeTVkMENJSGt6VTFvMF9HZzZiQmI2UFdjendPO" +
            "Dg1blF4dk5RSHd1Mjh1TEl0T0VObSJdLCJ0eXAiOiJvYXV0aC1hdXRoei1yZXErand0IiwiYWxnIjoiRVMyN" +
            "TYifQ.eyJyZXNwb25zZV91cmkiOiJodHRwOi8vLzE5Mi4xNjguOTQuMzc6ODA4MC9zZXJ2ZXIvdmVyaWZpZX" +
            "Ivb3BlbmlkNHZwUmVzcG9uc2U_c2Vzc2lvbklkPWIyNjE5YzYyMzM0YzkxZDE2ZGZjZmFlM2EzNmQ2MGNiIi" +
            "wicmVzcG9uc2VfdHlwZSI6InZwX3Rva2VuIiwicHJlc2VudGF0aW9uX2RlZmluaXRpb24iOnsiaW5wdXRfZG" +
            "VzY3JpcHRvcnMiOlt7ImZvcm1hdCI6eyJqd3RfdnAiOnsiYWxnIjpbIkVTMjU2Il19fSwiaWQiOiJodHRwcz" +
            "ovL2V4YW1wbGUuYm1pLmJ1bmQuZGUvY3JlZGVudGlhbC9waWQvMS4wIiwiY29uc3RyYWludHMiOnsibGltaX" +
            "RfZGlzY2xvc3VyZSI6InJlcXVpcmVkIiwiZmllbGRzIjpbeyJpbnRlbnRfdG9fcmV0YWluIjpmYWxzZSwicG" +
            "F0aCI6WyIkWydodHRwczovL2V4YW1wbGUuYm1pLmJ1bmQuZGUvY3JlZGVudGlhbC9waWQvMS4wJ11bJ2FnZV" +
            "9vdmVyXzE4J10iXX1dfX1dLCJpZCI6InJlcXVlc3QtVE9ETy1pZCJ9LCJzdGF0ZSI6ImIyNjE5YzYyMzM0Yz" +
            "kxZDE2ZGZjZmFlM2EzNmQ2MGNiIiwibm9uY2UiOiJhYzQxZDAwYTlmMDEwYmRiM2VlNzMwZmFlNmJiZTM4OC" +
            "IsImNsaWVudF9pZCI6Imh0dHA6Ly8xMC4wLjIuMjo4MDgwL3NlcnZlciIsImNsaWVudF9tZXRhZGF0YSI6ey" +
            "JhdXRob3JpemF0aW9uX2VuY3J5cHRlZF9yZXNwb25zZV9hbGciOiJFQ0RILUVTIiwiYXV0aG9yaXphdGlvbl" +
            "9lbmNyeXB0ZWRfcmVzcG9uc2VfZW5jIjoiQTEyOENCQy1IUzI1NiIsImp3a3MiOlt7Imt0eSI6IkVDIiwidX" +
            "NlIjoiZW5jIiwiY3J2IjoiUC0yNTYiLCJ4IjoiMEdpVXlVejFJamhFMDZCdml3LVFLcHNMV2NvUVl6RVhLM3" +
            "NCZUhlMjd1byIsInkiOiJMSC04c3dwaTZOem15aU5Oa1dBb3lOcG5Yd0xNVFh1M2VSLUg2dTQzUzhvIiwiYW" +
            "xnIjoiRUNESC1FUyJ9XSwicmVzcG9uc2VfbW9kZSI6ImRpcmVjdF9wb3N0Lmp3dCJ9LCJyZXNwb25zZV9tb2" +
            "RlIjoiZGlyZWN0X3Bvc3Quand0In0.6VvDuIZ6QhLbzVyncJ-3mEkykYAadSqmgpjxd72j6zxeivNxGpnPh0" +
            "c7YxCHbGNHY47ZZ7STu6LxbJY6EOWHHw"

    private fun createSingleUseReaderKey(): Pair<EcPrivateKey, X509CertChain> {
        val now = Clock.System.now()
        val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
        val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
        val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val readerKeySubject = "CN=OWF IC Online Verifier Single-Use Reader Key"

        // TODO: for now, instead of using the per-site Reader Root generated at first run, use the
        //  well-know OWF IC Reader root checked into Git.
        val owfIcReaderCert = X509Cert.fromPem("""
-----BEGIN CERTIFICATE-----
MIICCTCCAY+gAwIBAgIQZc/0rhdjZ9n3XoZYzpt2GjAKBggqhkjOPQQDAzA+MS8wLQYDVQQDDCZP
V0YgSWRlbnRpdHkgQ3JlZGVudGlhbCBURVNUIFJlYWRlciBDQTELMAkGA1UEBhMCWlowHhcNMjQw
OTE3MTY1NjA5WhcNMjkwOTE3MTY1NjA5WjA+MS8wLQYDVQQDDCZPV0YgSWRlbnRpdHkgQ3JlZGVu
dGlhbCBURVNUIFJlYWRlciBDQTELMAkGA1UEBhMCWlowdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATM
1ZVDQ7E4A+ujJl0J7Op8qvy/BSgg/UCTw+WrwYI32/jV9pk8Qu5BSTbUDZE2PQheqy4s3j8y1gMu
+Q5pemhYn/c4OMYXZY8uD+t4Wo9UFoSDkFbvlumZ/cuO5TTAI76jUjBQMB0GA1UdDgQWBBTgtILK
HJ50qO/Nc33zshz2aX4+4TAfBgNVHSMEGDAWgBTgtILKHJ50qO/Nc33zshz2aX4+4TAOBgNVHQ8B
Af8EBAMCAQYwCgYIKoZIzj0EAwMDaAAwZQIxALmOcU+Ggax3wHbD8tcd8umuDxzimf9PSICjvlh5
kwR0/1SZZF7bqMAOQXsrwNYFLgIwLVirmU4WvRlUktR2Ty5kxgDG0iy+g00ur9JXCF+wAUQjKHbg
VvIQ6NRr06GwpPJR
-----END CERTIFICATE-----
        """.trimIndent())

        val owfIcReaderRoot = EcPrivateKey.fromPem("""
-----BEGIN PRIVATE KEY-----
MFcCAQAwEAYHKoZIzj0CAQYFK4EEACIEQDA+AgEBBDDxgrZBXnoO54/hZM2DAGrByoWRatjH9hGs
lrW+vvdmRHBgS+ss56uWyYor6W7ah9ygBwYFK4EEACI=
-----END PRIVATE KEY-----
        """.trimIndent(),
            owfIcReaderCert.ecPublicKey)
        val readerKeyCertificate = MdocUtil.generateReaderCertificate(
            readerRootCert = owfIcReaderCert,
            readerRootKey = owfIcReaderRoot,
            readerKey = readerKey.publicKey,
            subject = X500Name.fromName(readerKeySubject),
            serial = ASN1Integer(1L),
            validFrom = validFrom,
            validUntil = validUntil
        )
        return Pair(
            readerKey,
            X509CertChain(listOf(readerKeyCertificate) + owfIcReaderCert)
        )
    }

    private fun generateSignedJWT(claimsSet: JWTClaimsSet) : SignedJWT {
        val (singleUseReaderKeyPriv, singleUseReaderKeyCertChain) = createSingleUseReaderKey()
        val readerPublic = singleUseReaderKeyPriv.publicKey.javaPublicKey as ECPublicKey
        val readerPrivate = singleUseReaderKeyPriv.javaPrivateKey as ECPrivateKey

        // TODO: b/393388152: ECKey is deprecated, but might be current library dependency.
        @Suppress("DEPRECATION")
        val readerKey = ECKey(
            Curve.P_256,
            readerPublic,
            readerPrivate,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )

        val readerX5c = singleUseReaderKeyCertChain.certificates.map { cert ->
            Base64.from(cert.encodedCertificate.toBase64Url())
        }

        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(readerKey.keyID)
                .x509CertChain(readerX5c)
                .type(JOSEObjectType("oauth-authz-req+jwt"))
                .build(),
            claimsSet
        )

        val signer: JWSSigner = ECDSASigner(readerKey)
        signedJWT.sign(signer)
        return signedJWT
    }

    private fun generateClaimSet(
        issuance: Instant,
        expiration: Instant,
        notBefore: Instant
    ): JWTClaimsSet =
        JWTClaimsSet.parse("{\n" +
            "    \"response_type\": \"vp_token\",\n" +
            "    \"client_id\": \"example.id\",\n" +
            "    \"response_uri\": \"https://example.com\",\n" +
            "    \"response_mode\": \"direct_post.jwt\",\n" +
            "    \"presentation_definition\": {\n" +
            "        \"id\": \"4db74328-9e94-49bb-97b7-bbfcb2d11a06\",\n" +
            "        \"input_descriptors\": [\n" +
            "            {\n" +
            "                \"id\": \"8f76f19e-b161-4baf-a57a-fd129322a48c\",\n" +
            "                \"format\": {\n" +
            "                    \"vc+sd-jwt\": {\n" +
            "                        \"sd-jwt_alg_values\": [\n" +
            "                            \"ES256\"\n" +
            "                        ],\n" +
            "                        \"kb-jwt_alg_values\": [\n" +
            "                            \"ES256\"\n" +
            "                        ]\n" +
            "                    }\n" +
            "                },\n" +
            "                \"constraints\": {\n" +
            "                    \"limit_disclosure\": \"required\",\n" +
            "                    \"fields\": [\n" +
            "                        {\n" +
            "                            \"path\": [\n" +
            "                                \"\$.age_equal_or_over.21\"\n" +
            "                            ]\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"path\": [\n" +
            "                                \"\$.vct\"\n" +
            "                            ],\n" +
            "                            \"filter\": {\n" +
            "                                \"type\": \"string\",\n" +
            "                                \"enum\": [\n" +
            "                                    \"https://example.bmi.bund.de/credential/pid/1.0\",\n" +
            "                                    \"urn:eu.europa.ec.eudi:pid:1\"\n" +
            "                                ]\n" +
            "                            }\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"exp\": ${expiration.epochSeconds},\n" +
            "    \"nbf\": ${notBefore.epochSeconds},\n" +
            "    \"iat\": ${issuance.epochSeconds},\n" +
            "    \"jti\": \"39543149-470b-47ae-be96-213f8f4bc9fd\"\n" +
            "}")

    @Test
    fun testParsePathBracketed() {
        Assert.assertEquals(Pair("namespace", "dataElem"), parsePathItem("\"\$['namespace']['dataElem']\""))
    }

    @Test
    fun testParsePathDotted() {
        Assert.assertEquals(Pair("credentialSubject", "dataElem"), parsePathItem("\"\$.dataElem\""))
    }

    @Test
    fun testExpiredJWT() {
        val now = Clock.System.now()
        
        val expiredClaimSet = generateClaimSet(
            issuance = now.minus(Duration.parse("1m")),
            expiration = now.minus(Duration.parse("1d")),
            notBefore = now.minus(Duration.parse("1m"))
        )

        Assert.assertThrows(RuntimeException::class.java) {
            getAuthRequestFromJwt(generateSignedJWT(expiredClaimSet), "example.id")
        }
    }

    @Test
    fun testIssuedInFutureJWT() {
        val now = Clock.System.now()

        val issuedInFutureClaimSet = generateClaimSet(
            issuance = now.plus(Duration.parse("1m")),
            expiration = now.plus(Duration.parse("1d")),
            notBefore = now.plus(Duration.parse("1m"))
        )

        Assert.assertThrows(RuntimeException::class.java) {
            getAuthRequestFromJwt(generateSignedJWT(issuedInFutureClaimSet), "example.id")
        }
    }

    @Test
    fun testNotYetValidJWT() {
        val now = Clock.System.now()

        val notYetValidClaimSet = generateClaimSet(
            issuance = now.minus(Duration.parse("1m")),
            expiration = now.plus(Duration.parse("1d")),
            notBefore = now.plus(Duration.parse("5m"))
        )

        Assert.assertThrows(RuntimeException::class.java) {
            getAuthRequestFromJwt(generateSignedJWT(notYetValidClaimSet), "example.id")
        }
    }

    @Test
    fun eudiwJwtToPresentationSubmission() {
        val authRequest = getAuthRequestFromJwt(SignedJWT.parse(eudiAgeOver18RequestObject), "verifier-backend.eudiw.dev")
        Assert.assertEquals(parseToJsonElement(
                "{\"input_descriptors\":" +
                    "[{\"id\":\"eu.europa.ec.eudiw.pid.1\"," +
                    "\"name\":\"EUDI PID\"," +
                    "\"purpose\":\"We need to verify your identity\"," +
                    "\"format\":{\"mso_mdoc\":{\"alg\":[\"ES256\",\"ES384\",\"ES512\",\"EdDSA\",\"ESB256\",\"ESB320\",\"ESB384\",\"ESB512\"]}}," +
                    "\"constraints\":{\"fields\":[{\"path\":[\"${'$'}['eu.europa.ec.eudiw.pid.1']['age_over_18']\"],\"intent_to_retain\":false}]}}]," +
                "\"id\":\"32f54163-7166-48f1-93d8-ff217bdb0653\"}"),
            authRequest.presentationDefinition
        )
        Assert.assertEquals("verifier-backend.eudiw.dev", authRequest.clientId)
        Assert.assertEquals("9b4f04a3-8328-4a87-b0f1-2350f62d4736", authRequest.nonce)
        Assert.assertEquals("https://verifier-backend.eudiw.dev/wallet/direct_post",
            authRequest.responseUri)
        Assert.assertEquals("44zZ7FmrM4nSuo5cJoQX2Gx1pKioAwVbN_gOaSOR1N-GGi2JfCky641R1UsSJT6bK2ALO4zUQ7SOVyc0mdy5kw",
            authRequest.state)
        Assert.assertEquals(parseToJsonElement(
                "{\"authorization_encrypted_response_alg\":\"ECDH-ES\"," +
                "\"authorization_encrypted_response_enc\":\"A128CBC-HS256\"," +
                "\"id_token_encrypted_response_alg\":\"RSA-OAEP-256\"," +
                "\"id_token_encrypted_response_enc\":\"A128CBC-HS256\"," +
                "\"jwks_uri\":\"https://verifier-backend.eudiw.dev/wallet/jarm/44zZ7FmrM4nSuo5cJoQX2Gx1pKioAwVbN_gOaSOR1N-GGi2JfCky641R1UsSJT6bK2ALO4zUQ7SOVyc0mdy5kw/jwks.json\"," +
                "\"subject_syntax_types_supported\":[\"urn:ietf:params:oauth:jwk-thumbprint\"]," +
                "\"id_token_signed_response_alg\":\"RS256\"}"),
            authRequest.clientMetadata!!)

        val presentationSubmission = createPresentationSubmission(authRequest, CredentialFormat.MDOC_MSO)
        Assert.assertEquals("32f54163-7166-48f1-93d8-ff217bdb0653",
            presentationSubmission.definitionId)
        val descriptorMaps = presentationSubmission.descriptorMaps
        for (descriptorMap: DescriptorMap in descriptorMaps) {
            Assert.assertEquals("eu.europa.ec.eudiw.pid.1", descriptorMap.id)
            Assert.assertEquals("mso_mdoc", descriptorMap.format)
            Assert.assertEquals("$", descriptorMap.path)
        }

        val docRequest = formatAsDocumentRequest(authRequest.presentationDefinition["input_descriptors"]!!.jsonArray[0].jsonObject)
        val expectedRequestedElems = listOf(
            DocumentRequest.DataElement("eu.europa.ec.eudiw.pid.1", "age_over_18", false))
        Assert.assertEquals(expectedRequestedElems, docRequest.requestedDataElements)
    }

    @Test
    fun annexBJwtToPresentationSubmission() {
        val authRequest = getAuthRequestFromJwt(SignedJWT.parse(annexBRequestObject), "example.com ")
        Assert.assertEquals(parseToJsonElement(
            "{\"input_descriptors\":" +
                    "[{\"id\":\"org.iso.18013.5.1.mDL \"," +
                    "\"format\":{\"mso_mdoc\":{\"alg\":[\"ES256\",\"ES384\",\"ES512\",\"EdDSA\",\"ESB256\",\"ESB320\",\"ESB384\",\"ESB512\"]}}," +
                    "\"constraints\":{\"fields\":" +
                        "[{\"path\":[\"${'$'}['org.iso.18013.5.1']['birth_date']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['document_number']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['driving_privileges']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['expiry_date']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['family_name']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['given_name']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['issue_date']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['issuing_authority']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['issuing_country']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['portrait']\"],\"intent_to_retain\":false}," +
                        "{\"path\":[\"${'$'}['org.iso.18013.5.1']['un_distinguishing_sign']\"],\"intent_to_retain\":false}]," +
                    "\"limit_disclosure\":\"required\"}}]," +
                    "\"id\":\"mDL-sample-req\"}\n"),
            authRequest.presentationDefinition
        )
        Assert.assertEquals("example.com ", authRequest.clientId)
        Assert.assertEquals("Safdaer�\$45_3342", authRequest.nonce)
        Assert.assertEquals("https://example.com/12345/response",
            authRequest.responseUri)
        Assert.assertEquals("34asfd34_34\$34", authRequest.state)
        Assert.assertEquals(parseToJsonElement(
            "{\"authorization_encrypted_response_alg\":\"ECDH-ES\"," +
                    "\"authorization_encrypted_response_enc\":\"A256GCM\"," +
                    "\"jwks\":{\"keys\":[{" +
                        "\"kty\":\"EC\"," +
                        "\"use\":\"enc\"," +
                        "\"crv\":\"P-256\"," +
                        "\"x\":\"xVLtZaPPK-xvruh1fEClNVTR6RCZBsQai2-DrnyKkxg\"," +
                        "\"y\":\"-5-QtFqJqGwOjEL3Ut89nrE0MeaUp5RozksKHpBiyw0\"," +
                        "\"alg\":\"ECDH-ES\"," +
                        "\"kid\":\"P8p0virRlh6fAkh5-YSeHt4EIv-hFGneYk14d8DF51w\"}]}," +
                    "\"vp_formats\":{\"mso_mdoc\":{\"alg\":[\"ES256\",\"ES384\",\"ES512\",\"EdDSA\",\"ESB256\",\"ESB320\",\"ESB384\",\"ESB512\"]}}}\n"),
            authRequest.clientMetadata!!)

        val presentationSubmission = createPresentationSubmission(authRequest, CredentialFormat.MDOC_MSO)
        val descriptorMaps = presentationSubmission.descriptorMaps
        for (descriptorMap: DescriptorMap in descriptorMaps) {
            Assert.assertEquals("org.iso.18013.5.1.mDL ", descriptorMap.id)
            Assert.assertEquals("mso_mdoc", descriptorMap.format)
            Assert.assertEquals("$", descriptorMap.path)
        }

        val docRequest = formatAsDocumentRequest(authRequest.presentationDefinition["input_descriptors"]!!.jsonArray[0].jsonObject)
        val expectedRequestedElems = listOf(
            DocumentRequest.DataElement("org.iso.18013.5.1", "birth_date", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "document_number", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "driving_privileges", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "expiry_date", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "family_name", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "given_name", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "issue_date", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "issuing_authority", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "issuing_country", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "portrait", false),
            DocumentRequest.DataElement("org.iso.18013.5.1", "un_distinguishing_sign", false))
        Assert.assertEquals(expectedRequestedElems, docRequest.requestedDataElements)
    }
}
