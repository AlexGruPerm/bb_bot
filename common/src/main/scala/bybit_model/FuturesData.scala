package bybit_model

import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

case class ApiRespFuturesData(
                               retCode: Int,
                               retMsg: String,
                               result: FuturesDataResult
                             )

object ApiRespFuturesData {
  implicit val encoder: JsonEncoder[ApiRespFuturesData] = DeriveJsonEncoder.gen[ApiRespFuturesData]
  implicit val decoder: JsonDecoder[ApiRespFuturesData] = DeriveJsonDecoder.gen[ApiRespFuturesData]
}

case class FuturesDataResult(
                              category: String,
                              list: List[FuturesData]
                            )

object FuturesDataResult {
  implicit val encoder: JsonEncoder[FuturesDataResult] = DeriveJsonEncoder.gen[FuturesDataResult]
  implicit val decoder: JsonDecoder[FuturesDataResult] = DeriveJsonDecoder.gen[FuturesDataResult]
}

case class FuturesData(
                        symbol: String,
                        lastPrice: String,
                        indexPrice: String,
                        markPrice: String,
                        prevPrice24h: String,
                        price24hPcnt: String,
                        highPrice24h: String,
                        lowPrice24h: String,
                        prevPrice1h: String,
                        openInterest: String,
                        openInterestValue: String,
                        turnover24h: String,
                        volume24h: String,
                        fundingRate: String,
                        nextFundingTime: String,
                        ask1Size: String,
                        bid1Price: String,
                        ask1Price: String,
                        bid1Size: String,
                        fundingIntervalHour: String,
                        fundingCap: String
                      )

object FuturesData {
  implicit val encoder: JsonEncoder[FuturesData] = DeriveJsonEncoder.gen[FuturesData]
  implicit val decoder: JsonDecoder[FuturesData] = DeriveJsonDecoder.gen[FuturesData]
}

// Mapping for db (data.futures_data)
case class FuturesDataRow(
                           idSymbol: Int,
                           lastPrice: Option[BigDecimal],
                           indexPrice: Option[BigDecimal],
                           markPrice: Option[BigDecimal],
                           prevPrice24h: Option[BigDecimal],
                           price24hPcnt: Option[BigDecimal],
                           highPrice24h: Option[BigDecimal],
                           lowPrice24h: Option[BigDecimal],
                           prevPrice1h: Option[BigDecimal],
                           openInterest: Option[BigDecimal],
                           openInterestValue: Option[BigDecimal],
                           turnover24h: Option[BigDecimal],
                           volume24h: Option[BigDecimal],
                           fundingRate: Option[BigDecimal],
                           nextFundingTime: Option[Long],
                           ask1Size: Option[BigDecimal],
                           bid1Price: Option[BigDecimal],
                           ask1Price: Option[BigDecimal],
                           bid1Size: Option[BigDecimal],
                           fundingIntervalHour: Option[Int],
                           fundingCap: Option[BigDecimal]
                         )

object FuturesDataRow {

  private def toOptBigDecimal(s: String): Option[BigDecimal] = {
    if (s == null || s.isEmpty) None
    else scala.util.Try(BigDecimal(s)).toOption
  }

  private def toOptLong(s: String): Option[Long] = {
    if (s == null || s.isEmpty) None
    else scala.util.Try(s.toLong).toOption
  }

  private def toOptInt(s: String): Option[Int] = {
    if (s == null || s.isEmpty) None
    else scala.util.Try(s.toInt).toOption
  }

  // Converter from response to db row
  def fromFuturesData(data: FuturesData, idSymbol: Int): FuturesDataRow = {
    FuturesDataRow(
      idSymbol = idSymbol,
      lastPrice = toOptBigDecimal(data.lastPrice),
      indexPrice = toOptBigDecimal(data.indexPrice),
      markPrice = toOptBigDecimal(data.markPrice),
      prevPrice24h = toOptBigDecimal(data.prevPrice24h),
      price24hPcnt = toOptBigDecimal(data.price24hPcnt),
      highPrice24h = toOptBigDecimal(data.highPrice24h),
      lowPrice24h = toOptBigDecimal(data.lowPrice24h),
      prevPrice1h = toOptBigDecimal(data.prevPrice1h),
      openInterest = toOptBigDecimal(data.openInterest),
      openInterestValue = toOptBigDecimal(data.openInterestValue),
      turnover24h = toOptBigDecimal(data.turnover24h),
      volume24h = toOptBigDecimal(data.volume24h),
      fundingRate = toOptBigDecimal(data.fundingRate),
      nextFundingTime = toOptLong(data.nextFundingTime),
      ask1Size = toOptBigDecimal(data.ask1Size),
      bid1Price = toOptBigDecimal(data.bid1Price),
      ask1Price = toOptBigDecimal(data.ask1Price),
      bid1Size = toOptBigDecimal(data.bid1Size),
      fundingIntervalHour = toOptInt(data.fundingIntervalHour),
      fundingCap = toOptBigDecimal(data.fundingCap)
    )
  }
}