/*
  Copyright (c) 2011,2012, 
   Saswat Anand (saswat@gatech.edu)
   Mayur Naik  (naik@cc.gatech.edu)
  All rights reserved.
  
  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met: 
  
  1. Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer. 
  2. Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution. 
  
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  
  The views and conclusions contained in the software and documentation are those
  of the authors and should not be interpreted as representing official policies, 
  either expressed or implied, of the FreeBSD Project.
*/


package z3;

import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class Z3Task extends ExecTask {
  private int id;

  private static String Z3_SMT = "z3smt.";
  private static String Z3_OUT = "z3out.";
  private static String Z3_ERR = "z3err.";
  File smtFile, z3OutFile, z3ErrFile;

  private static String args = "-c \"z3 -smt2 ";

  private Target target = new Target();

  boolean sat;

  HashMap<String, String> answers = new HashMap<String, String>();

  public Z3Task(int id) {
    this.id = id;
    initTask();
  }

  private void initTask() {
    setExecutable("/bin/sh");

    Project project = new Project();
    setProject(project);

    target.setName("runZ3");
    target.addTask(this);
    project.addTarget(target);
    target.setProject(project);

    project.init();
  }

  public void sendMessage(String msg) {
    PrintWriter smtWriter = null;
    try {
      smtFile = Util.newOutFile("", Z3_SMT + id);

      z3OutFile = Util.newOutFile("", Z3_OUT + id);

      z3ErrFile = Util.newOutFile("", Z3_ERR + id);

      smtWriter = Util.newWriter(smtFile, true);
      smtWriter.println(msg);

      smtWriter.close();

      exec(z3OutFile, z3ErrFile, smtFile.getAbsolutePath());

      String z3OutFileName = z3OutFile.getAbsolutePath();
      retrieveResult(z3OutFileName);

    } catch (IOException e) {
      System.err.println(e.getMessage());

    }
  }

  private void retrieveResult(String z3OutFileName) {
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(z3OutFileName));

      String line = br.readLine();

      while (line != null) {

        if (line.equals("sat")) {
          sat = true;
        } else if (line.equals("unsat")) {
          sat = false;
          break;
        }
        if (line.contains("ERROR") || line.contains("error")) {
          String oldline = line;
          line = br.readLine();
          throw new RuntimeException("Z3 encuntered an error in its input: " + oldline + "\n" + line);
        } else if (line.startsWith("(model") && sat) {
          if (line.endsWith(")")) break;
          line = br.readLine();
          process(line);
          while (!line.endsWith(")")) {
            line = br.readLine();
            process(line);
          }

          break;
        }
        line = br.readLine();

        //br.close();
      }
    } catch (FileNotFoundException e) {
      System.err.println(e.getMessage());
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }

  private void process(String line) {
    String words[] = line.split(" ");
    String varName = words[1];
    StringBuilder sb = new StringBuilder();
    for (int i = 2; i < words[2].length(); i++) {
      char c = words[2].charAt(i);
      if (Character.isDigit(c)) {
        sb.append(c);
      } else {
        break;
      }
    }
    BigInteger bi = new BigInteger(sb.toString());
    sb = new StringBuilder();
    for (int i = 1; i < 8 - bi.bitLength() % 8; i++) {
      sb.append("0");
    }
    for (int i = bi.bitLength(); i >= 0; i--) {
      if (bi.testBit(i))
        sb.append("1");
      else
        sb.append("0");
    }
    answers.put(varName, sb.toString());
  }

  public HashMap<String, String> getAnswer() {
    return answers;
  }

  public void exec(File outFile, File errFile, String file) {
    Commandline.Argument cmdLineArgs = createArg();
    String args2 = args + file;

    args2 += " 1>" + outFile;
    args2 += " 2>" + errFile;

    args2 = args2 + "\"";

    cmdLineArgs.setLine(args2);

    //setError(errFile);
    //setOutput(outFile);

    target.execute();

    this.logFlush();

  }

  public static void testMessage() {
    String msg = "(declare-const a Int)\n" +
        "(declare-const b Int)\n" +
        "(assert (not (= a b)))" +
        "\n(check-sat)\n(get-model)";

    Z3Task task = new Z3Task(1);
    task.sendMessage(msg);
    for (Entry<String, String> entry : task.getAnswer().entrySet()) {
      System.out.println(entry.getKey() + ": " + entry.getValue());
    }
  }

  public static void main(String[] args) throws IOException {
    testMessage();
  }
}
