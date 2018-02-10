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
package config;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

import java.io.File;
import java.util.StringTokenizer;

public class Configuration {

//  public final static String opt_image = "e";
  public final static String opt_tdir = "tdir";

  public final static String opt_help = "help";

  public final static String opt_rmm_pso = "pso";//for testing only

  public final static String opt_window_size = "window_size";
  public final static String opt_schedule = "schedule";
  public final static String opt_no_branch = "nobranch";
  public final static String opt_no_volatile = "novolatile";
  public final static String opt_allrace = "allrace";
  private final static String opt_fast_detect = "fast_detect";

  public final static String opt_all_consistent = "allconsistent";
  public final static String opt_constraint_outdir = "outdir";
  public final static String opt_solver_timeout = "solver_timeout";
  public final static String opt_solver_memory = "solver_memory";
  public final static String opt_timeout = "timeout";
  public final static String opt_symbolizer="symbolizer";

  public final static String opt_smtlib1 = "smtlib1";
  public final static String opt_optrace = "optrace";

//  public final static String default_max_len = "100000";
//  public final static String default_solver_timeout = "60";
  public final static String default_solver_timeout = "100";
  public final static String default_solver_memory = "2000";
  public final static String default_empty = "";
  public final static String default_timeout = "36000";
  public final static String default_symbolizer="llvm";

  public final static String default_constraint_outdir = System.getProperty("user.dir") +
      System.getProperty("file.separator") + "z3_tmp";

  public String appname;
  public static String symbolizer;
  public long window_size;
  public long solver_timeout;
  public long solver_memory;
  public long timeout;
  public boolean fast_detect;

  public String constraint_outdir;
  public boolean nobranch;
  public boolean schedule;
  public boolean optrace;
  public boolean allrace;
  public boolean novolatile;

  public boolean allconsistent;
  public boolean rmm_pso;
  public boolean smtlib1;

//  public String binaryImage;
  public String traceDir;

  private boolean help;



  public Configuration(String[] args) {

    try {

//      if (args.length == 0) {
//        printUsageAndExit();
//      }

      //emp.Example stringbuffer.StringBufferTest

      //String[] args2 = {"abc","-maxlen","10000","-noschedule","-nobranch"};

      // create Options object
      Options options = new Options();

      // add t option
//      options.addOption(opt_no_schedule, false, "not report schedule");
//      options.addOption(opt_no_branch, false, "use no branch model");
//      options.addOption(opt_no_volatile, false, "exclude volatile variables");
//      options.addOption(opt_all_consistent, false, "require all read-write consistent");
//      options.addOption(opt_rmm_pso, false, "PSO memory model");
//      options.addOption(opt_smtlib1, false, "use constraint format smtlib v1.2");
//      options.addOption(opt_optrace, false, "optimize race detection");
//      options.addOption(opt_allrace, false, "check all races");
//
//      options.addOption(opt_constraint_outdir, false, "constraint file directory");
//      options.addOption(opt_timeout, false, "rvpredict timeout in seconds");

      options.addOption(opt_solver_timeout, true, "solver timeout in seconds");
      options.addOption(opt_solver_memory, true, "solver memory fsize in MB");
      options.addOption(opt_window_size, true, "window fsize");
      options.addOption(opt_tdir, true, "trace dir");
      options.addOption(opt_symbolizer, true, "symbolizer");


      options.addOption(opt_help, false, "print help info");

      CommandLineParser parser = new BasicParser();
      CommandLine cmd = parser.parse(options, args);

      String strWinSz = cmd.getOptionValue(opt_window_size, "50000");
      if (strWinSz != null)
        window_size = Long.valueOf(strWinSz);
      else
        window_size = 50000;

      String z3timeout = cmd.getOptionValue(opt_solver_timeout, default_solver_timeout);
      solver_timeout = Long.valueOf(z3timeout);

      String z3memory = cmd.getOptionValue(opt_solver_memory, default_solver_memory);
      solver_memory = Long.valueOf(z3memory);

      String rvtimeout = cmd.getOptionValue(opt_timeout, default_timeout);
      timeout = Long.valueOf(rvtimeout);
      
      constraint_outdir = cmd.getOptionValue(opt_constraint_outdir, default_constraint_outdir);

      constraint_outdir = constraint_outdir.replace(File.separatorChar, '.');

      fast_detect = cmd.hasOption(opt_fast_detect);
      //ok, let's make fast_detect by default
      fast_detect = true;

      schedule = cmd.hasOption(opt_schedule);


      rmm_pso = cmd.hasOption(opt_rmm_pso);
      //rmm_pso = true;

      nobranch = cmd.hasOption(opt_no_branch);
      novolatile = cmd.hasOption(opt_no_volatile);

      allconsistent = cmd.hasOption(opt_all_consistent);
      smtlib1 = cmd.hasOption(opt_smtlib1);
      optrace = cmd.hasOption(opt_optrace);
      allrace = cmd.hasOption(opt_allrace);

      //by default optrace is true
      optrace = true;

      help = cmd.hasOption(opt_help);

      if (help
    		  //|| cmd.getArgList().isEmpty()
    		  ) {
        printUsageAndExit();
      }

//      binaryImage = cmd.getOptionValue(opt_image);
      traceDir = cmd.getOptionValue(opt_tdir);

      if (cmd.getArgList().size() > 0)
        appname = (String) cmd.getArgList().get(0);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }

  }
  
  private void printUsageAndExit() {
	
	  System.out.println(getUsage());
	  System.exit(0);
}

private static String getUsage() {
    return "\nGeneral Options:\n"
        + padOpt(" -help", "print this message")
        + padOpt(" -maxlen SIZE", "set window fsize to SIZE")
        + padOpt(" -schedule", "generate racey schedules")
        + padOpt(" -nobranch", "disable control flow (MCM)")
        + padOpt(" -novolatile", "exclude races on volatile variables")
        + padOpt(" -allconsistent", "require all read-write consistency (Said)")
        + padOpt(" -smtlib1", "use smtlib v1 format")
        + padOpt(" -outdir PATH", "constraint file directory to PATH")
        + padOpt(" -solver_timeout TIME", "set solver timeout to TIME seconds")
        + padOpt(" -solver_memory MEMORY", "set memory used by solver to MEMORY megabytes")
        + padOpt(" -timeout TIME", "set rvpredict timeout to TIME seconds")
        ;
  }

  protected static String padOpt(String opts, String desc) {
    return pad(1, opts, 30, desc);
  }

  private static String pad(int initial, String opts, int tab, String desc) {
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < initial; i++) b.append(" ");
    b.append(opts);
    int i;
    if (tab <= opts.length()) {
      b.append("\n");
      i = 0;
    } else i = opts.length() + initial;
    for (; i <= tab; i++) {
      b.append(" ");
    }
    for (StringTokenizer t = new StringTokenizer(desc);
         t.hasMoreTokens(); ) {
      String s = t.nextToken();
      if (i + s.length() > 78) {
        b.append("\n");
        i = 0;
        for (; i <= tab; i++) {
          b.append(" ");
        }
      }
      b.append(s);
      b.append(" ");
      i += s.length() + 1;
    }
    b.append("\n");
    return b.toString();
  }

  @Override
  public String toString() {
    return "Configuration{" +
        "appname='" + appname + '\'' +
        ", window_size=" + window_size +
        ", solver_timeout=" + solver_timeout +
        ", solver_memory=" + solver_memory +
        ", timeout=" + timeout +
        ", constraint_outdir='" + constraint_outdir + '\'' +
        ", nobranch=" + nobranch +
        ", noschedule=" + schedule +
        ", optrace=" + optrace +
        ", allrace=" + allrace +
        ", novolatile=" + novolatile +
        ", allconsistent=" + allconsistent +
        ", rmm_pso=" + rmm_pso +
        ", smtlib1=" + smtlib1 +
        ", traceDir='" + traceDir + '\'' +
        ", help=" + help +
        '}';
  }
}
