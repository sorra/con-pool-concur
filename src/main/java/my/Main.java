package my;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import com.alibaba.druid.pool.DruidDataSourceFactory;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;

// Mainly simulating the scene of a heavy-load RPC system
// My benchmark of long waits (on MacBook Pro 15):
//   200 threads -> 0%, 300 threads -> 0.1%, 400 threads -> 0.3%, 500 threads -> 2.6%
public class Main {
  static DataSource dataSource;
  static AtomicInteger count = new AtomicInteger(0);
  public static void main(String[] args) throws Exception {
    Class.forName("org.h2.Driver");
    Properties props = new Properties();
    props.load(Main.class.getClassLoader().getResourceAsStream("db.properties"));
    dataSource = DruidDataSourceFactory.createDataSource(props);
    // maxActive = 60
    Connection[] conns = new Connection[60];
    for (int i = 0; i < 60; i++) {
      conns[i] = dataSource.getConnection();
    }
    for (Connection each : conns) {
      each.close();
    }
    multiThreads();
  }

  static void multiThreads() throws InterruptedException {
    int THREADS = 500;
    ExecutorService es = Executors.newFixedThreadPool(THREADS);
    for (int i = 0; i < THREADS; i++) {
      es.submit(() -> {
        for(int j = 0; j < 20; j++) {
          try {
            hold(10);
            hold(10);
            hold(10);
            Thread.sleep(200); // simulates idle thread after completing a task
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    }
    es.shutdown();
    es.awaitTermination(3, TimeUnit.MINUTES);
    System.out.println("Long wait: " + count.get());
  }

  // SQL execution holds the connection
  static void hold(long time) throws SQLException, InterruptedException {
    long start = currentTimeMillis();
    Connection conn = dataSource.getConnection();
    long cost = currentTimeMillis() - start;
    if (cost>100) {
      out.print("\n"+cost+" ms ");
      count.incrementAndGet();
    }
    Thread.sleep(time); // simulates SQL execution
    conn.close();
  }
}
