package local.arca;
import java.util.concurrent.*;
public final class BodyWorkCheck {
 public static void main(String[]args)throws Exception{
  Thread.currentThread().interrupt();BodyWork.checkpoint();Thread.interrupted();
  CountDownLatch started=new CountDownLatch(1), stopped=new CountDownLatch(1);
  int[] checks={0};
  FutureTask<Void> task=new FutureTask<>(()->{
   for(int i=0;i<20000;i++){BodyWork.checkpoint();checks[0]++;}
   started.countDown();
   try {while(true)BodyWork.checkpoint();} finally {stopped.countDown();}
  });
  Thread worker=new Thread(task);BodyWork.workerCreated(worker);worker.start();
  if(!started.await(2,TimeUnit.SECONDS))throw new AssertionError("active worker failed");
  task.cancel(true);
  if(!stopped.await(2,TimeUnit.SECONDS))throw new AssertionError("cancelled worker did not stop");
  worker.join(2000);
  if(worker.isAlive()||!task.isCancelled()||checks[0]!=20000)throw new AssertionError();
  BodyWork.checkpoint();
  System.out.println("PASS active worker, cancelled FutureTask exits, unrelated interrupted thread unaffected");
 }
}
