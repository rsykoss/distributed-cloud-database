package de.tum.i13.client;

public class HidePasswordFromCommandLine extends Thread {
  boolean stopThread = false;
  boolean hideInput = false;

  public void run() {
    try {
      sleep(500);
    } catch (InterruptedException e) {
    }
    while (!stopThread) {
      if (hideInput) {
        System.out.print("\b*");
      }
      try {
        sleep(1);
      } catch (InterruptedException e) {
      }
    }
  }
}
