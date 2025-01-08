package com.iaphub

import java.util.Date

internal object IaphubLogLimit {

  val timeLimit: Int = 60
  val countLimit: Int = 10
  var count: Int = 0
  var time: Date = Date()

  fun isAllowed(): Boolean {
    if ((Date()).getTime() > (this.time.getTime() + (this.timeLimit * 1000))) {
      this.count = 0
      this.time = Date()
    }

    this.count++

    return this.count <= this.countLimit
  }

}