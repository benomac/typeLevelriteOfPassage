package com.rockthejvm.jobsboard.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

case class PaginationConfig(defaultPageSize: Int, defaultLimit: Int) derives ConfigReader
