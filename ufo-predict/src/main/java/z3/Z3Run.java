/*******************************************************************************
 * Copyright (c) 2013 University of Illinois
 * <p/>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p/>
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p/>
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package z3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map.Entry;

import config.Configuration;
import aser.ufo.UFO;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2LongRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Constraint solving with Z3 solver
 *
 * @author jeffhuang
 *
 */
public class Z3Run {
  private static final String SOLVER_FULL_STRING = "/usr/bin/z3"; // TODO: add option to change path in config? ANDREW
protected static String Z3_SMT2 = ".z3smt2";
  protected static String Z3_OUT = ".z3out";
  protected static String Z3_ERR = ".z3err.";

  Configuration config;
  
  File smtFile, z3OutFile, z3ErrFile;
  protected String CMD;

  public Z3Model model;
  public LongArrayList schedule;

  public Z3Run(Configuration config, int id) {
    try {
      init(config, id);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }

  /**
   * initialize solver configuration
   * @param config
   * @param id
   * @throws IOException
   */
  protected void init(Configuration config, int id) throws IOException {

	 this.config = config;
    String fileNameBase = config.appname.replace(File.separatorChar, '.');

    //constraint file
    smtFile = Util.newOutFile(config.constraint_outdir, fileNameBase + "_" + id + Z3_SMT2);

    //solution file
    z3OutFile = Util.newOutFile(config.constraint_outdir, fileNameBase + "_" + id + Z3_OUT);

    //z3ErrFile = Util.newOutFile(Z3_ERR+taskId);//looks useless

    //command line to Z3 solver
    CMD = SOLVER_FULL_STRING+" -T:" + config.solver_timeout + " -memory:" + config.solver_memory + " -smt2 ";
  }

  /**
   * solve constraint "msg"
   * @param msg
   */
  public LongArrayList buildSchedule(String msg) {

//    System.out.println(">>>Z3Run buildSchedule:" + msg);

    PrintWriter smtWriter = null;
    try {
      smtWriter = Util.newWriter(smtFile, true);
      smtWriter.println(msg);
      smtWriter.close();

      //invoke the solver
      exec(z3OutFile, z3ErrFile, smtFile.getAbsolutePath());

      model = Z3ModelReader.read(z3OutFile);

      if (model != null) {
    	  	
    	  	//We can skip schedule construction if we don't need it
    	  	if(config.schedule)
    	  		schedule = computeSchedule2(model);
    	  	else
    	  		schedule =  new LongArrayList(); 
    	  		
      }
      //String z3OutFileName = z3OutFile.getAbsolutePath();
      //retrieveResult(z3OutFileName);
      if (z3ErrFile != null)
        z3ErrFile.delete();
      if (smtFile != null)
        smtFile.delete();
//
      z3OutFile.delete();
      z3OutFile.getParentFile().delete();
    } catch (IOException e) {
      System.err.println(e.getMessage());

    }
    return schedule;
  }

  public static Long varName2GID(String name) {
    return Long.parseLong(name.substring(1));
  }

  public LongArrayList computeSchedule2(Z3Model model) {
	    LongArrayList ls = new LongArrayList(model.getMap().size());
	    IntArrayList orderls = new IntArrayList(model.getMap().size());
    for (Entry<String, Object> entryModel : model.getMap().entrySet()) {
      String op = entryModel.getKey();
      Long gid = varName2GID(op);
      int order = ((Number) entryModel.getValue()).intValue();
      int index=0;
      
      for(;index<ls.size();index++)
    	  	if(order<=orderls.get(index))
    	  		break;
      orderls.add(index,order);
      ls.add(index, gid);
    }
     
    return ls;
  }
  /**
   * Given the model of solution, return the corresponding schedule
   *
   *
   * @param model
   * @return
   */
  public ArrayList<String> computeSchedule(Z3Model model) {

    ArrayList<String> schedule = new ArrayList<String>(UFO.INITSZ_S);

    for (Entry<String, Object> entryModel : model.getMap().entrySet()) {
      String op = entryModel.getKey();
      int order = ((Number) entryModel.getValue()).intValue();
      if (schedule.isEmpty())
        schedule.add(op);
      else
        for (int i = 0; i < schedule.size(); i++) {
          int ord = ((Number) model.getMap().get(schedule.get(i))).intValue();
          if (order < ord) {
            schedule.add(i, op);
            break;
          } else if (i == schedule.size() - 1) {
            schedule.add(op);
            break;
          }

        }
    }

    return schedule;
  }

  public void exec(File outFile, File errFile, String file) throws IOException {

    String cmds = CMD + file;

//		args2 += " 1>"+outFile;
//		args2 += " 2>"+errFile;
//
//		args2 = args2 + "\"";

    //cmds = "/usr/local/bin/z3 -version";

    Process process = Runtime.getRuntime().exec(cmds);
    InputStream inputStream = process.getInputStream();

    //do we need to wait for Z3 to finish?

    // write the inputStream to a FileOutputStream
    OutputStream out = new FileOutputStream(outFile);

    int read = 0;
    byte[] bytes = new byte[8192];

    while ((read = inputStream.read(bytes)) != -1) {
      out.write(bytes, 0, read);
    }

    inputStream.close();
    out.flush();
    out.close();
    //setError(errFile);
    //setOutput(outFile);

  }

}
