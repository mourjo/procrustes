package me.mourjo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RunnableQueueBuilder {

  public static BlockingQueue<Runnable> buildQueue(int capacity) {
    return new ArrayBlockingQueue<Runnable>(capacity);
  }

}
