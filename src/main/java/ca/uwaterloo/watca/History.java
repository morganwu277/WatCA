package ca.uwaterloo.watca;

import java.io.*;
import java.util.*;

/**
 * @author Wojciech Golab, Shankha Subhra Chatterjee
 */
public class History {
  public static final String INITIAL_VALUE = "";

  private String key;
  private SortedMap<String, Cluster> clusterMap;
  private SortedSet<Cluster> clusterSet;

  public History(String key) {
    this.key = key;
    clusterMap = new TreeMap();
    clusterSet = new TreeSet();

    Cluster initCluster = new Cluster(key, INITIAL_VALUE);
    Operation initWrite = new Operation(key, INITIAL_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, "W");
    initCluster.addOperation(initWrite);
    clusterMap.put(INITIAL_VALUE, initCluster);
    clusterSet.add(initCluster);
  }

  @Override
  public String toString() {
    return "History{" +
        "key='" + key + '\'' +
        ", clusterMap=" + clusterMap +
        '}';
  }

  public String getKey() {
    return key;
  }

  public void addOperationList(List<Operation> opList) {
    for (Operation op : opList) {
      this.addOperation(op);
    }
  }

  public void addOperation(Operation op) {
    String value = op.getValue();
    if (value == null && op.isRead())
      return;

    Cluster clu = clusterMap.get(value);
    if (clu == null) {
      clu = new Cluster(key, value);
      clusterMap.put(value, clu);
    } else {
      // sort order of cluster may change
      clusterSet.remove(clu);
    }
    clu.addOperation(op);
    clusterSet.add(clu);
  }

  public void deleteOld(long timeThreshold) {
    deleteOldClusters(timeThreshold);
  }

  public void deleteOldClusters(long timeThreshold) {
    List<Cluster> deleteList = new ArrayList();
    for (Cluster c : clusterSet) {
      if (c.getRight() < timeThreshold) {
        deleteList.add(c);
        clusterMap.remove(c.getValue());
      } else {
        break;
      }
    }
    clusterSet.removeAll(deleteList);
  }

  public synchronized List<Long> getScores(ScoreFunction sfn) {
    List<Long> ret;
//    try {
//      PrintWriter out = new PrintWriter(new FileWriter("scores.txt", true));
      PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out));
      ret = logScores(sfn, out);
//      out.close();
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
    return ret;
  }

  public List<Long> logScores(ScoreFunction sfn, PrintWriter out) {
    List<Long> ret = new ArrayList();
    for (Cluster a : clusterSet) {
      long tempScore = a.getScore();
      SortedSet<Cluster> tail = clusterSet.tailSet(a);
      for (Cluster b : tail) {
        if (a == b) {
          long newScore = Collections.max(sfn.getScores(a, b));
          if (newScore > 0) {
            tempScore = Math.max(tempScore, newScore);
            if (out != null)
              out.println("Key = " + a.getKey() + ", Value = " + a.getValue() + ", Score = " + newScore);
          }
        } else if (a.overlaps(b)) {
          long newScore = Collections.max(sfn.getScores(a, b));
          if (newScore > 0) {
            tempScore = Math.max(tempScore, newScore);
            if (b.getScore() < newScore) {
              b.setScore(newScore);
            }
            if (out != null)
              out.println("Key = " + a.getKey() + ", ValueA = " + a.getValue() + ", ValueB = " + b.getValue() + ", Score = " + newScore);
          }
        } else {
          break;
        }
      }
      a.setScore(tempScore);
      // Log scores
      if (tempScore == 0)
        if (out != null) {
          // out.println("Key = " + a.getKey() + ", Value = " + a.getValue() + ", Score = " + tempScore);
        }

      ret.add(tempScore);
    }
    return ret;
  }
}

