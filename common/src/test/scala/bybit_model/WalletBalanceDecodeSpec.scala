package bybit_model

import zio.json.DecoderOps
import zio.test._

/**
 * Bybit may answer /v5/account/wallet-balance with an empty `result.list`; that must decode to an empty balance instead
 * of failing with a misleading `result.list(missing)` error.
 */
object WalletBalanceDecodeSpec extends ZIOSpecDefault {

  private val emptyListJson =
    """{"retCode":0,"retMsg":"OK","result":{"list":[]},"retExtInfo":{},"time":1700000000000}"""

  private val emptyListNoMetaJson =
    """{"retCode":0,"retMsg":"OK","result":{"list":[]}}"""

  private val oneAccountJson =
    """{"retCode":0,"retMsg":"OK","result":{"list":[{"totalEquity":"100.5","totalInitialMargin":"",
      |"totalAvailableBalance":"90","totalWalletBalance":"100.5","coin":[{"equity":"100","usdValue":"100",
      |"borrowAmount":"0","walletBalance":"100","cumRealisedPnl":"0","coin":"USDT"}]}]},
      |"retExtInfo":{},"time":1700000000000}""".stripMargin

  def spec: Spec[TestEnvironment, Any] =
    suite("WalletBalance decoding")(
      test("an empty result list decodes to an empty balance") {
        assertTrue(emptyListJson.fromJson[ApiRespWalletBalance].exists(_.result.balance.isEmpty))
      },
      test("missing retExtInfo and time do not fail decoding") {
        assertTrue(emptyListNoMetaJson.fromJson[ApiRespWalletBalance].exists(_.result.balance.isEmpty))
      },
      test("an account snapshot is decoded with its coins") {
        val parsed = oneAccountJson.fromJson[ApiRespWalletBalance]
        assertTrue(
          parsed.exists(_.result.balance.exists(_.coin.map(_.coin) == List("USDT"))),
          parsed.exists(_.result.balance.exists(_.totalEquity == 100.5))
        )
      }
    )
}
