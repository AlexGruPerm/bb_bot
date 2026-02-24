package bybit_model

case class ViewDeepLine(
  code: Option[String],
  p_first: Option[Double],
  c_last: Option[Double],
  dif_prcnt: Option[Double],
  move_dir: String,
  smpl_volat: Option[Double]
)
