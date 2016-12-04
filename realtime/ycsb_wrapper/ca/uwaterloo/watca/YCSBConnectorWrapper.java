package ca.uwaterloo.watca;

import com.yahoo.ycsb.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Wojciech Golab
 */
public class YCSBConnectorWrapper extends DB {

  static private final String KEY_NOT_FOUND = "key_not_found";
  static private final String NO_DATA = "no_data";
  static private final Lock lock = new ReentrantLock();
  static private BufferedWriter out;
  static private BufferedWriter outLocal;
  static private int numThreads = 0;
  static private String serverId = "";
  private DB innerDB;
  static private long readDelay;
  static private long writeDelay;

  static private long maxExecutionTime;
  static private long startTime;

  static private AtomicBoolean done = new AtomicBoolean(false);
  static private String connectorClassName;

  public YCSBConnectorWrapper() throws DBException {
    try {
      connectorClassName = System.getProperties().getProperty("analysis.ConnectorClass");
      innerDB = (DB) Class.forName(connectorClassName).newInstance();

    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      throw new DBException("Unable to instantiate wrapped connector object.", e);
    }
  }

  public void setProperties(Properties p) {
    innerDB.setProperties(p);
  }

  public Properties getProperties() {
    return innerDB.getProperties();
  }

  @Override
  public void init() throws DBException {
    innerDB.init();

    serverId = System.getProperties().getProperty("mySeq");
    if (serverId == "") {
      throw new RuntimeException("Please provide mySeq parameter as serverId.");
    }

    try {
      lock.lock();

      if (startTime == 0) {
        String MAX_EXECUTION_TIME = "maxexecutiontime";
        maxExecutionTime = Integer.parseInt(getProperties().getProperty(MAX_EXECUTION_TIME, "0"));
        startTime = System.currentTimeMillis();
      }

      if (out == null) {
        readDelay = Integer.parseInt(getProperties().getProperty("readdelay", "0"));
        writeDelay = Integer.parseInt(getProperties().getProperty("writedelay", "0"));
        System.out.println("Read delay: " + readDelay);
        System.out.println("Write delay: " + writeDelay);
        System.out.println("Read consistency level: " + getProperties().getProperty("cassandra.readconsistencylevel", "ONE"));
        System.out.println("Write consistency level: " + getProperties().getProperty("cassandra.writeconsistencylevel", "ONE"));
        String logHost = System.getProperties().getProperty("analysis.LogHost");
        String logPort = System.getProperties().getProperty("analysis.LogPort");
        String logFileName = System.getProperties().getProperty("analysis.LogFile");
        if (logHost == null || logPort == null) {
          System.out.println("Opening log file: " + logFileName);
          out = new BufferedWriter(new FileWriter(logFileName, true));
          System.out.println("Opened log file: " + logFileName);
        } else {
          System.out.println("Opening log stream: " + logHost + ":" + logPort);
          Socket socket = new Socket(logHost, Integer.parseInt(logPort));
          out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
          System.out.println("Opened log stream: " + logHost + ":" + logPort);
          System.out.println("Opening log file: " + logFileName);
          outLocal = new BufferedWriter(new FileWriter(logFileName, true));
          System.out.println("Opened log file: " + logFileName);
        }
        numThreads++;
      }
    } catch (IOException e) {
      throw new DBException("Unable to open consistency log.", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void cleanup() throws DBException {
    done.set(true);
    innerDB.cleanup();
    try {
      lock.lock();
      if (out != null) {
        out.flush();
      }
      if (outLocal != null) {
        outLocal.flush();
      }
    } catch (IOException e) {
      throw new DBException("Unable to flush consistency log file.", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  protected void finalize() throws Throwable {
    try {
      try {
        lock.lock();
        if (out != null) {
          out.close();
        }
        if (outLocal != null) {
          outLocal.close();
        }
      } catch (IOException e) {
        throw new RuntimeException("Unable to close consistency log file.", e);
      } finally {
        lock.unlock();
      }
    } finally {
      super.finalize();
    }
  }

  @Override
  public Status read(String table, String key, Set<String> fields, HashMap<String, ByteIterator> result) {
    long start, finish;
    start = System.currentTimeMillis();
    try {
      Thread.sleep(readDelay);
    } catch (InterruptedException ex) {
    }
    long now = System.currentTimeMillis();
    Status ret = Status.OK;

    ret = innerDB.read(table, key, fields, result);
    finish = System.currentTimeMillis();
    String value = "";
    if (!result.isEmpty()) {
      ByteIterator b;
      if (connectorClassName.contains("CassandraCQLClient")) {
        // for cassandra2-binding use "field0" as key to access
        // "value", "y_id" as key to access "key" in result:
        b = result.get("field0");
      } else {
        b = result.get(result.keySet().iterator().next());
      }
      value = new String(b.toArray());
    }
    long endTime = System.currentTimeMillis();
    // only log operations that return proper values
    //if (!value.equals("key_not_found"))
    logOperation("R", serverId + key, value, start, finish);
    localLogOperation("R", serverId + key, value, start, finish);
    return ret;
  }

  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
    return innerDB.scan(table, startkey, recordcount, fields, result);
  }

  @Override
  public Status update(String table, String key, HashMap<String, ByteIterator> values) {
    long start, finish;
    start = System.currentTimeMillis();
    HashMap<String, String> values2 = StringByteIterator.getStringMap(values);
    Status ret = innerDB.update(table, key, StringByteIterator.getByteIteratorMap(values2));
    try {
      Thread.sleep(writeDelay);
    } catch (InterruptedException ex) {
    }
    finish = System.currentTimeMillis();
    String value = values2.get(values2.keySet().iterator().next());
    logOperation("U", serverId + key, value, start, finish);
    localLogOperation("U", serverId + key, value, start, finish);
    return ret;
  }

  @Override
  public Status insert(String table, String key, HashMap<String, ByteIterator> values) {
    long start, finish;
    start = System.currentTimeMillis();
    HashMap<String, String> values2 = StringByteIterator.getStringMap(values);
    Status ret = innerDB.insert(table, key, StringByteIterator.getByteIteratorMap(values2));
    try {
      Thread.sleep(writeDelay);
    } catch (InterruptedException ex) {
    }
    finish = System.currentTimeMillis();
    String value = values2.get(values2.keySet().iterator().next());
    logOperation("I", serverId + key, value, start, finish);
    localLogOperation("I", serverId + key, value, start, finish);
    return ret;
  }

  @Override
  public Status delete(String table, String key) {
    return innerDB.delete(table, key);
  }

  /**
   * if already used local log, this invocation will immediatedly return without put log locally
   *
   * @param operationType
   * @param key
   * @param value
   * @param startTime
   * @param finishTime
   */
  private void localLogOperation(String operationType, String key, String value, long startTime, long finishTime) {
    String logHost = System.getProperties().getProperty("analysis.LogHost");
    String logPort = System.getProperties().getProperty("analysis.LogPort");
    if (logHost == null || logPort == null) { // means it already used a local log, don't use this log any more
      return;
    }
    try {
      lock.lock();
      if (outLocal != null) {
        outLocal.write(logString(operationType, key, value, startTime, finishTime));
        outLocal.newLine();
      }
    } catch (Exception e) {
      String logFileName = System.getProperties().getProperty("analysis.LogFile");
      e.printStackTrace();
      throw new RuntimeException("Unable to write consistency log file " + logFileName, e);
    } finally {
      lock.unlock();
    }
  }

  private void logOperation(String operationType, String key, String value, long startTime, long finishTime) {
    try {
      lock.lock();
      if (out != null) {
        out.write(logString(operationType, key, value, startTime, finishTime));
        out.newLine();
      }
    } catch (Exception e) {
      String logFileName = System.getProperties().getProperty("analysis.LogFile");
      e.printStackTrace();
      throw new RuntimeException("Unable to write consistency log file " + logFileName, e);
    } finally {
      lock.unlock();
    }
  }

  private static String sha1(String input) throws NoSuchAlgorithmException {
    MessageDigest mDigest = MessageDigest.getInstance("SHA1");
    byte[] result = mDigest.digest(input.getBytes());
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < result.length; i++) {
      sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
    }

    return sb.toString();
  }

  private String logString(String operationType, String key, String value, long startTime, long finishTime) {
    StringBuffer res = new StringBuffer();
    try {
      res.append(key);
      res.append("\t");
      if (value.equals(KEY_NOT_FOUND)) {
        res.append(KEY_NOT_FOUND);
      } else if (value.equals("")) {
        res.append(NO_DATA);
      } else {
        res.append(sha1(value));
      }
      res.append("\t");
      res.append(String.valueOf(startTime));
      res.append("\t");
      res.append(String.valueOf(finishTime));
      res.append("\t");
      res.append(operationType);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      throw new RuntimeException("Uable to encode value with sha1 algorithm ", e);
    }
    return res.toString();
  }
}
