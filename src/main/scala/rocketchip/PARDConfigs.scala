// See LICENSE for license details.

package rocketchip

import coreplex._
import config._

class PARDFPGAConfig extends Config(new FPGAConfig
  ++ new WithoutFPU
  ++ new WithExtMemSize(0x80000000L)
  ++ new WithNCores(2)
  ++ new WithRTCPeriod(5) // gives 10 MHz RTC assuming 50 MHz uncore clock
  ++ new BaseConfig)