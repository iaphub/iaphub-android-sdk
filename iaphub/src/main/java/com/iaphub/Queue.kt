package com.iaphub

import java.util.Date

internal class QueueItem<T> {

  val date: Date
  val data: T

  constructor(data: T) {
    this.date = Date()
    this.data = data
  }

}

internal class Queue<T> {

  val iterator: (item: QueueItem<T>, completion: () -> Unit) -> Unit
  var waiting: MutableList<QueueItem<T>> = mutableListOf()
  var completionQueue: MutableList<() -> Unit> = mutableListOf()
  var isRunning: Boolean = false
  var isPaused: Boolean = false

  constructor(iterator: (item: QueueItem<T>, completion: () -> Unit) -> Unit) {
    this.iterator = iterator
  }

  /*
   * Add item to the queue
   */
  fun add(data: T) {
    synchronized(this@Queue) {
      this.waiting.add(QueueItem(data))
    }
    this.run()
  }

  /*
   * Pause queue
   */
  fun pause() {
    this.isPaused = true
  }

  /*
   * Resume queue
   */
  fun resume(completion: (() -> Unit)? = null) {
    this.isPaused = false
    this.run(completion=completion)
  }

  /******************************** PRIVATE ********************************/

  /*
   * Run queue
   */
  private fun run(isInitialRun: Boolean = true, completion: (() -> Unit)? = null) {
    if (isInitialRun) {
      synchronized(this@Queue) {
        // Add completion to the queue
        if (completion != null) {
          this.completionQueue.add(completion)
        }
        // Stop here if the queue is already running
        if (this.isRunning) {
          return
        }
        // Mark as running
        this.isRunning = true
      }
    }
    // Get the items we're going to process and empty waiting list
    val items = this.waiting
    this.waiting = mutableListOf()
    // Execute iterator for each item
    Util.eachSeries<QueueItem<T>, Error>(
      items,
      { item, itemCompletion ->
        this.iterator(item) { -> itemCompletion(null) }
      },
      { _ ->
        // Run again if there's more items in the waiting list
        if (this.waiting.size != 0 && this.isPaused == false) {
          this.run(false)
        }
        // Otherwise we're done
        else {
          // Mark the queue as not running
          this.isRunning = false
          // Execute completion queue
          this.completionQueue.forEach() { complete -> complete()}
          this.completionQueue = mutableListOf()
        }
      }
    )
  }
}