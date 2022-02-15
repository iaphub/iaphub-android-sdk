package com.iaphub

import net.jodah.concurrentunit.Waiter
import org.junit.*
import java.util.Timer
import kotlin.concurrent.thread
import kotlin.concurrent.timerTask

class QueueTest {

  private var queue: Queue<Int>? = null

  @Test
  fun addQueue() {
    val self = this
    val waiter = Waiter()
    val results: MutableList<Int> = mutableListOf()
    this.queue = Queue() { item, completion ->
      Timer().schedule(timerTask {
        results.add(item.data)
        completion()
      }, 200)
    }

    thread {
      Timer().schedule(timerTask {
        self.queue?.add(1)
      }, 1)
    }
    thread {
      Timer().schedule(timerTask {
        self.queue?.add(2)
      }, 1)
    }
    thread {
      Timer().schedule(timerTask {
        self.queue?.add(3)
      }, 1)
    }
    thread {
      Timer().schedule(timerTask {
        self.queue?.add(4)
      }, 1)
    }
    thread {
      Timer().schedule(timerTask {
        self.queue?.add(5)
      }, 1)
    }
    thread {
      Timer().schedule(timerTask {
        self.queue?.add(6)
      }, 1)
    }

    this.queue?.resume {
      val reorderedResults: List<Int> = results.toSortedSet().toList()
      waiter.assertEquals(listOf(1, 2, 3, 4, 5, 6), reorderedResults)
      waiter.resume()
    }

    waiter.await(10000)
  }

}