package ca.uwaterloo.watca;


import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Shankha Subhra Chatterjee, Wojciech Golab
 */
public class Analyzer {
  private final List<Operation> operations;
  private ConcurrentHashMap<String, History> keyHistMap;
  private ScoreFunction sfn;

  private String scoreOutput;

  public Analyzer(String filename) {
    operations = new ArrayList();
    keyHistMap = new ConcurrentHashMap<>();
    // default score function
    sfn = new GKScoreFunction();
    scoreOutput = filename;
  }

  public void computeMetrics() throws IOException {
    List<Operation> temp = new ArrayList();
    // the values(for zones) within the new batch ops
    HashMap<String, HashSet<String>> keyValueSet = new HashMap<>();

    synchronized (operations) {
      temp.addAll(operations);
      operations.clear();
    }

    // first add new operations, multiple operation on the same key
    ConcurrentMap<String, List<Operation>> keyOpsMap = temp.parallelStream().collect(Collectors.groupingByConcurrent(Operation::getKey));

    for (Map.Entry<String, List<Operation>> entry : keyOpsMap.entrySet()) {
      String key = entry.getKey();
      List<Operation> opList = entry.getValue();
      History h = keyHistMap.get(key);
      if (h == null) {
        h = new History(key);
        h.addOperationList(opList);
        keyHistMap.put(key, h);
      } else {
        h.addOperationList(opList);
      }
    }

    PrintWriter logWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(scoreOutput), "utf-8")));

    // compute scores for each history
    ConcurrentMap<String, List<Long>> keyScoreMap = new ConcurrentHashMap();
    // ScoreFunction sfn = new RegularScoreFunction();
    keyHistMap.entrySet().parallelStream().forEach((e) -> {
      keyScoreMap.put(e.getKey(), e.getValue().logScores(sfn, logWriter));
    });

    logWriter.close();
  }

  public void processOperation(Operation op) {
    synchronized (operations) {
      operations.add(op);
    }
  }
}

