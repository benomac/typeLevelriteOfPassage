package com.rockthejvm.jobsboard.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

case class PostgresConfig(nThreads: Int, url: String, user: String, pass: String)
    derives ConfigReader
