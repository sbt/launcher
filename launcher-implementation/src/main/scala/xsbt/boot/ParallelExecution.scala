package xsbt.boot

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

private[xsbt] object ParallelExecution {
  protected[xsbt] val executionContext =
    // ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        Runtime.getRuntime.availableProcessors()
      )
    )

}
