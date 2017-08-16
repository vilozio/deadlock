
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created on 16.08.2017.
 *
 * @see
 * <a
 * href="http://korhner.github.io/java/multithreading/detect-java-deadlocks-programmatically/">
 *  How to detect Java deadlocks programmatically
 * </a>
 *
 * @author Kirill M. Korotkov
 * @author Igor Chernenko
 */
public class Deadlock {
    A a = new A();
    B b = new B();

    int c = 0;

    class A {
        String foo() {
            return "foo";
        }
    }

    class B {
        String bar() {
            return "bar";
        }
    }

    /**
     * Делаем дедлок
     */
    public static void main2(String[] args) {
        Deadlock d = new Deadlock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                d.foo1();
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                d.bar1();
            }
        });

        t1.start();
        t2.start();
    }

    void foo1() {
        synchronized (a) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            synchronized (b) {
                System.out.println(b.bar());
//            }
            bar1();
        }
    }

    void bar1() {
        synchronized (b) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (a) {
                System.out.println(a.foo());
            }
        }
    }


    /**
     * Сравниваем volatile int c и synchronized
     */
    public static void main3(String[] args) throws InterruptedException {
        Deadlock d = new Deadlock();
        Thread t1 = new Thread(() -> d.count());
        Thread t2 = new Thread(() -> d.count());
        t1.start();
        t2.start();
        Thread.sleep(3000);
        System.out.println(d.c);
    }

    synchronized void count() {
        for (int i = 0; i < 10000; i++) {
            c++;
        }
    }


    /**
     * Отслеживаем дедлоки с помощью ThreadMXBean
     */
    public static void main(String[] args) {
        DeadlockHandler deadlockHandler = new DeadlockHandler() {
            @Override
            public void handleDeadlock(ThreadInfo[] deadlockedThreads) {
                if (deadlockedThreads != null) {
                    System.err.println("Deadlock detected!");

//                    Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
                    for (ThreadInfo threadInfo : deadlockedThreads) {

                        if (threadInfo != null) {

                            for (Thread thread : Thread.getAllStackTraces().keySet()) {

                                if (thread.getId() == threadInfo.getThreadId()) {
                                    System.err.println(threadInfo.toString().trim());

                                    for (StackTraceElement ste : thread.getStackTrace()) {
                                        System.err.println("\t" + ste.toString().trim());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };

        Deadlock d = new Deadlock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                d.foo1();
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                d.bar1();
            }
        });

        DeadlockDetector detector = new DeadlockDetector(deadlockHandler,
                1000, TimeUnit.MILLISECONDS);
        detector.start();

        t1.start();
        t2.start();


    }


}

interface DeadlockHandler {
    void handleDeadlock(final ThreadInfo[] deadlockedThreads);
}

class DeadlockDetector {

    private final DeadlockHandler deadlockHandler;
    private final long period;
    private final TimeUnit unit;
    private final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    final Runnable deadlockCheck = new Runnable() {
        @Override
        public void run() {
            long[] deadlockedThreadIds = DeadlockDetector.this.mbean.findDeadlockedThreads();

            if (deadlockedThreadIds != null) {
                ThreadInfo[] threadInfos =
                        DeadlockDetector.this.mbean.getThreadInfo(deadlockedThreadIds);

                DeadlockDetector.this.deadlockHandler.handleDeadlock(threadInfos);
            }
        }
    };

        public DeadlockDetector(final DeadlockHandler deadlockHandler,
                            final long period, final TimeUnit unit) {
        this.deadlockHandler = deadlockHandler;
        this.period = period;
        this.unit = unit;
    }

    public void start() {
        this.scheduler.scheduleAtFixedRate(
                this.deadlockCheck, this.period, this.period, this.unit);
    }
}
