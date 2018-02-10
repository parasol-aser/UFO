package z3;

import config.Configuration;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.*;

public class Z3FastRun extends Z3Run {

  public Z3FastRun(Configuration config, int id) {
    super(config, id);
  }

  public boolean feasible(String msg) {

//    System.out.println(">>>Z3Run buildSchedule:" + msg);

    PrintWriter smtWriter = null;
    try {
      smtWriter = Util.newWriter(smtFile, true);
      smtWriter.println(msg);
      smtWriter.close();

      //invoke the solver

      String cmds = CMD + smtFile.getAbsolutePath();

      Process process = Runtime.getRuntime().exec(cmds);
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      boolean sat = false;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("unsat")) {
          sat = false;
          break;
        } else if (line.startsWith("sat")) {
          sat = true;
          break;
        }
      }
      reader.close();

      return sat;
      //String z3OutFileName = z3OutFile.getAbsolutePath();
      //retrieveResult(z3OutFileName);
//      if (z3ErrFile != null)
//        z3ErrFile.delete();
//      if (smtFile != null)
//        smtFile.delete();
//
//      z3OutFile.delete();
//      z3OutFile.getParentFile().delete();
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
    return false;
  }
}
