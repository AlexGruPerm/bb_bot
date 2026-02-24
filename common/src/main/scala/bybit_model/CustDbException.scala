package bybit_model

object CustDbException {

  val ErrInputException: Exception =
    new Exception("Empty input. Please use json config file name as input parameter.")

  val UnknownDbException: Exception =
    new Exception("Unknown db type. Driver string must contain: postgresql,oracle,clickhouse")

}
